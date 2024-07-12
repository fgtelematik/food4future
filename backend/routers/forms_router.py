#  f4f Study Companion Backend Server
#
#  Copyright (c) 2024 Technical University of Applied Sciences Wildau
#  Author: Philipp Wagner, Research Group Telematics
#  Contact: fgtelematik@th-wildau.de
#
#  This program is free software; you can redistribute it and/or
#  modify it under the terms of the GNU General Public License
#  as published by the Free Software Foundation version 2.
#
#  This program is distributed in the hope that it will be useful,
#  but WITHOUT ANY WARRANTY; without even the implied warranty of
#  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
#  GNU General Public License for more details.
#
#  You should have received a copy of the GNU General Public License
#  along with this program; if not, write to the Free Software
#  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

from typing import List

from fastapi import APIRouter, Depends, HTTPException
from pymongo.database import Database

from enums import Role
from models import PyObjectId, to_mongo, db
from routers.session import currentUser
from schema_v2.legacy_converter import regenerate_legacy_schema
from schema_v2.schema_enums import IdentifierCheckResult
from schema_v2.schema_models import InputForm, InputField, preserved_identifiers, InputEnum

router = APIRouter(
    prefix="/forms",
    tags=["Forms and Fields Access"]
)


def check_identifier_internal(collection: Database, new_identifier: str, current_identifier: str | None = None):
    if current_identifier and new_identifier == current_identifier:
        return IdentifierCheckResult.OK

    if new_identifier in preserved_identifiers:
        return IdentifierCheckResult.Reserved

    if not new_identifier:  # TODO: Other criteria for invalid?
        return IdentifierCheckResult.Invalid

    field = collection.find_one({"identifier": new_identifier})

    if field:
        return IdentifierCheckResult.AlreadyInUse

    return IdentifierCheckResult.OK


@router.get("/", include_in_schema=False)
@router.get("",
            response_model=List[InputForm],
            description="Returns a list of all available forms.\n"
                        " form is an ordered collection of InputFields, which can either be used as:\n"
                        "\t- a daily questionnaire to be filled out by the participant\n"
                        "\t- a form to collect static participant data filled out by the researcher\n"
                        "\t- a subform used as entry data set in another form, list or subform\n"
            )
def get_forms(user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to list forms")

    forms = db.input_form.find()
    forms = [InputForm(**form) for form in forms]

    return forms


@router.put("/form",
            response_model=InputForm,
            description="Creates or updates a form if an id field is provided.")
def upsert_form(form: InputForm, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to create or update forms")

    form_data = to_mongo(form)

    if form.id:
        # Update existing form
        old_form_data = db.input_form.find_one({"_id": form.id})
        if not old_form_data:
            raise HTTPException(status_code=404, detail="Existing form not found")

        old_form = InputForm(**old_form_data)

        # Check if identifier is reserved or already in use
        identifier_check = check_identifier_internal(db.input_form, form.identifier, old_form.identifier)
        if identifier_check != IdentifierCheckResult.OK:
            raise HTTPException(status_code=409, detail="Identifier is reserved or already in use")

        db.input_form.update_one({"_id": form.id}, {"$set": form_data})
    else:
        # Check if identifier is reserved or already in use
        identifier_check = check_identifier_internal(db.input_form, form.identifier)

        if identifier_check != IdentifierCheckResult.OK:
            raise HTTPException(status_code=409, detail="Identifier is reserved or already in use")

        # Create new form
        form.id = db.input_form.insert_one(form_data).inserted_id

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return form


@router.delete("/form/{form_id}",
               description="Deletes a form.")
def delete_form(form_id: PyObjectId, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to delete forms")

    form = db.input_form.find_one({"_id": form_id})

    if not form:
        raise HTTPException(status_code=404, detail="Form not found")

    db.input_form.delete_one({"_id": form_id})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()


@router.get("/fields",
            response_model=List[InputField],
            description="Returns a list of all available form fields.\n"
                        "Input fields can be assigned to forms.")
def get_fields(user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to list input fields")

    fields = db.input_field.find()
    fields = [InputField(**field) for field in fields]

    return fields


@router.put("/field",
            response_model=InputField,
            description="Creates or updates an input field if an id field is provided.")
def upsert_field(field: InputField, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to create or update input fields")

    field_data = to_mongo(field)

    if field.id:
        # Update existing field
        old_field_data = db.input_field.find_one({"_id": field.id})

        if not old_field_data:
            raise HTTPException(status_code=404, detail="Existing input field not found")

        old_field = InputField(**old_field_data)

        # Check if identifier is reserved or already in use
        identifier_check = check_identifier_internal(db.input_field, field.identifier, old_field.identifier)
        if identifier_check != IdentifierCheckResult.OK:
            raise HTTPException(status_code=409, detail="Identifier is reserved or already in use")

        db.input_field.update_one({"_id": field.id}, {"$set": field_data})
    else:
        # Create new field

        # Check if identifier is reserved or already in use
        identifier_check = check_identifier_internal(db.input_field, field.identifier)
        if identifier_check != IdentifierCheckResult.OK:
            raise HTTPException(status_code=409, detail="Identifier is reserved or already in use")

        field.id = db.input_field.insert_one(field_data).inserted_id

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return field


@router.delete("/field/{field_id}",
               description="Deletes an input field.")
def delete_field(field_id: PyObjectId, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to delete input fields")

    field = db.input_field.find_one({"_id": field_id})

    if not field:
        raise HTTPException(status_code=404, detail="Input field not found")

    db.input_field.delete_one({"_id": field_id})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()


@router.get("/enums",
            response_model=List[InputEnum],
            description="Returns a list of all available input enums.")
def get_enums(user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to list enums")

    enums = db.input_enum.find()
    enums = [InputEnum(**enum) for enum in enums]

    return enums


@router.put("/enum",
            response_model=InputEnum,
            description="Creates or updates an input enum if an id field is provided.")
def upsert_enum(enum: InputEnum, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to create or update enums")

    enum_data = to_mongo(enum)

    if enum.id:
        # Update existing enum
        old_enum = db.input_enum.find_one({"_id": enum.id})
        if not old_enum:
            raise HTTPException(status_code=404, detail="Existing enum not found")

        db.input_enum.update_one({"_id": enum.id}, {"$set": enum_data})
    else:
        # Create new enum
        enum.id = db.input_enum.insert_one(enum_data).inserted_id

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return enum


@router.delete("/enum/{enum_id}",
               description="Deletes an input enum.")
def delete_enum(enum_id: PyObjectId, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to delete enums")

    enum = db.input_enum.find_one({"_id": enum_id})

    if not enum:
        raise HTTPException(status_code=404, detail="Enum not found")

    db.input_enum.delete_one({"_id": enum_id})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()


@router.get("/enum/check_identifier/{identifier:path}",
            description="Checks if an input enum identifier is reserved or already in use.",
            response_model=IdentifierCheckResult)
def check_enum_identifier(identifier: str, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to check enum identifiers")

    return check_identifier_internal(db.input_enum, identifier)


@router.get("/field/check_identifier/{identifier:path}",
            description="Checks if an input field identifier is reserved or already in use.",
            response_model=IdentifierCheckResult)
def check_field_identifier(identifier: str, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to check input field identifiers")

    return check_identifier_internal(db.input_field, identifier)


@router.get("/check_identifier/{identifier:path}",
            description="Checks if an input form identifier is reserved or already in use.",
            response_model=IdentifierCheckResult)
def check_form_identifier(identifier: str, user=Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to check form identifiers")

    return check_identifier_internal(db.input_form, identifier)

# -- Debugging helper -- #
# Substitute any request handler functions with the following
# function for debugging HTTP 422 errors:
#
# async def debug_request(req: Request):
#     print(req)
#     ans = await req.json()
#     field = InputField(**ans) # Replace InputField with the desired Pydantic type!
#     raise HTTPException(status_code=500, detail="Debug only.")
