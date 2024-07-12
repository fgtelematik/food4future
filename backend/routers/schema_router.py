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

import json
from pathlib import Path
from uuid import uuid4

from fastapi import UploadFile, APIRouter, Depends, HTTPException, Form, File
from starlette.requests import Request

from enums import Role
from models import User, db, PyObjectId, to_mongo
from schema_v2.legacy_converter import regenerate_legacy_schema
from schema_v2.schema_models import FoodImage, Study, FoodEnum, FoodEnumItem, preserved_identifiers
from routers.session import currentUser

router = APIRouter(
    prefix="/schema",
    tags=["Schema Access"]
)


############
# Food Image
############

@router.post("/foodimage",
             response_model=FoodImage,
             description="Upload a food image and create or update a corresponding FoodImage dataset. The metadata must be stored as the food_image_json field, which must be a JSON string with the schema of FoodImage. "
                         "If `food_image.id` is set, this will repalce an existing FoodImage dataset, if unset a new "
                         "will be created.\n\nNote: The FoodImage will be automatically be deleted from the server if it"
                         " is no longer be used by any Consumable or ConsumableCategory (after it was used once)."
                         "\nThe value of the `filename` field will be ignored and overwritten by the uploaded file.")
async def upload_food_image(image_file: UploadFile = File(),
                            food_image_json: str = Form(),
                            user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to upload images")

    food_image_json_parsed = json.loads(food_image_json)

    if "filename" in food_image_json_parsed:
        del food_image_json_parsed["filename"]

    food_image = FoodImage(**food_image_json_parsed)

    if food_image.id:
        # Update existing FoodImage
        old_foodimage = db.food_images.find_one({"_id": food_image.id})
        if not old_foodimage:
            raise HTTPException(status_code=404, detail="Existing FoodImage not found")

    ext = image_file.filename.split(".")[-1]
    if ext not in ["jpg", "jpeg", "png"]:
        raise HTTPException(status_code=400, detail="Invalid file extension")

    filename = f"{uuid4()}.{ext}"

    # TODO: Make image upload path configurable
    upload_path = "images"

    Path(upload_path).mkdir(parents=True, exist_ok=True)

    with open(f"images/{filename}", "wb") as buffer:
        buffer.write(image_file.file.read())

    food_image.filename = filename

    if food_image.id:
        # Delete old image file
        Path(f"{upload_path}/{old_foodimage['filename']}").unlink(missing_ok=True)

        # Update existing FoodImage metadata
        db.food_images.update_one({"_id": food_image.id}, {"$set": to_mongo(food_image)})
    else:
        # Create new FoodImage
        food_image.id = db.food_images.insert_one(to_mongo(food_image)).inserted_id

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return food_image


@router.put("/foodimage", response_model=FoodImage,
            description="Edit the metadata for an existing foodimage.\nUse POST /foodimage for create new or replace with new image.", )
async def edit_food_image(req: Request,
                          food_image: FoodImage,
                          user: User = Depends(currentUser)):
    body = await req.body()
    print(body)
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to edit FoodImages")

    if not food_image.id:
        raise HTTPException(status_code=400, detail="FoodImage ID not set")

    old_food_image = db.food_images.find_one({"_id": food_image.id})
    if not old_food_image:
        raise HTTPException(status_code=404, detail="FoodImage not found")

    db.food_images.update_one({"_id": food_image.id}, {"$set": to_mongo(food_image, exclude={"id", "filename"})})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return food_image


@router.get("/foodimage/{foodimage_uuid}", response_model=FoodImage, description="Get a FoodImage dataset")
async def get_food_image(foodimage_uuid: PyObjectId, user: User = Depends(currentUser)):
    food_image = db.food_images.find_one({"_id": foodimage_uuid})
    if not food_image:
        raise HTTPException(status_code=404, detail="FoodImage not found")

    food_image = FoodImage(**food_image)

    return food_image


@router.get("/foodimage/by_filename/{image_filename:path}", response_model=FoodImage,
            description="Get a FoodImage dataset by filename")
async def get_image_info_by_filename(image_filename):
    image_filename = str.replace(image_filename, "images/", "")  # Remove legacy images/ prefix if present

    food_image = db.food_images.find_one({"filename": image_filename})
    if not food_image:
        raise HTTPException(status_code=404, detail="FoodImage not found")

    food_image = FoodImage(**food_image)

    return food_image


@router.get("/foodimages", response_model=list[FoodImage], description="Get a list of FoodImage datasets")
async def get_food_images(user: User = Depends(currentUser)):
    food_images = db.food_images.find()
    food_images = [FoodImage(**food_image) for food_image in food_images]

    return food_images


@router.delete("/foodimage/{foodimage_uuid}",
               description="Delete a FoodImage data set and its corresponding image file")
async def delete_food_image(foodimage_uuid: PyObjectId, user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to delete images")

    food_image = db.food_images.find_one({"_id": foodimage_uuid})
    if not food_image:
        raise HTTPException(status_code=404, detail="FoodImage not found")

    food_image = FoodImage(**food_image)
    Path(f"images/{food_image.filename}").unlink(missing_ok=True)

    db.food_images.delete_one({"_id": foodimage_uuid})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()


########
# Study
########


async def upsert_study(request: Request):
    study = Study(**await request.json())

    return ""


@router.put("/study", response_model=Study, description="Create a new or update an existing study. ", )
async def upsert_study(study: Study,
                       user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to create or update studies")

    study_data = to_mongo(study)

    if study.id:
        # Update existing study
        old_study = db.studies.find_one({"_id": study.id})
        if not old_study:
            raise HTTPException(status_code=404, detail="Existing study not found")

        db.studies.update_one({"_id": study.id}, {"$set": study_data})
    else:
        # Create new study
        study.id = db.studies.insert_one(study_data).inserted_id

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return study


@router.get("/studies", response_model=list[Study], description="Get a list of studies")
async def get_studies(user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to list studies")

    studies = db.studies.find()
    studies = [Study(**study) for study in studies]

    return studies


@router.delete("/study/{study_uuid}", description="Delete a study")
async def delete_study(study_uuid: PyObjectId, user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to delete studies")

    study = db.studies.find_one({"_id": study_uuid})
    if not study:
        raise HTTPException(status_code=404, detail="Study not found")

    study_users = db.user.find({"study_id": study_uuid})

    for study_user in study_users:
        study_user_id = study_user["_id"]

        db.session.delete_many({"user_id": study_user_id})
        db.user_data.delete_many({"user_id": study_user_id})
        db.sensor_data.delete_many({"user_id": study_user_id})
        db.requests.delete_many({"$or":
                                     [{"request_user": study_user_id}, {"target_user": study_user_id}]})
        db.syncs.delete_many({"user_id": study_user_id})
        db.user.delete_one({"_id": study_user_id})

    db.studies.delete_one({"_id": study_uuid})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()


############
# FoodEnum
############

@router.put("/foodenum", response_model=FoodEnum, description="Create a new or update an existing FoodEnum.")
async def upsert_foodenum(foodenum: FoodEnum, user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to create or update FoodEnum")

    foodenum_with_same_identifier = db.food_enums.find_one({"identifier": foodenum.identifier})
    if foodenum_with_same_identifier and foodenum_with_same_identifier["_id"] != foodenum.id:
        raise HTTPException(status_code=409, detail="A FoodEnum with the same identifier already exists")

    if foodenum.identifier in preserved_identifiers:
        raise HTTPException(status_code=409,
                            detail="The identifier %s is reserved and cannot be used for FoodEnum" % foodenum.identifier)

    if foodenum.id:
        # Update existing FoodEnum
        old_foodenum = db.food_enums.find_one({"_id": foodenum.id})

        if not old_foodenum:
            raise HTTPException(status_code=404, detail="Existing FoodEnum not found")

        db.food_enums.update_one({"_id": foodenum.id}, {"$set": to_mongo(foodenum)})
    else:
        # Create new FoodEnum
        foodenum.id = db.food_enums.insert_one(to_mongo(foodenum)).inserted_id

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return foodenum


@router.get("/foodenums", response_model=list[FoodEnum], description="Get a list of FoodEnum datasets")
async def get_foodenums(user: User = Depends(currentUser)):
    foodenums = db.food_enums.find()
    return [FoodEnum(**foodenum) for foodenum in foodenums]


@router.delete("/foodenum/{foodenum_uuid}", description="Delete a FoodEnum")
async def delete_foodenum(foodenum_uuid: PyObjectId, user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to delete FoodEnum")

    foodenum = db.food_enums.find_one({"_id": foodenum_uuid})
    if not foodenum:
        raise HTTPException(status_code=404, detail="FoodEnum not found")

    db.food_enums.delete_one({"_id": foodenum_uuid})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()


##############
# FoodEnumItem
##############


@router.put("/foodenumitem", response_model=FoodEnumItem,
            description="Create a new or update an existing FoodEnumItem.")
async def upsert_foodenumitem(foodenumitem: FoodEnumItem, user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to create or update FoodEnumItems")

    foodenumitem_with_same_identifier = db.food_enum_items.find_one({"identifier": foodenumitem.identifier})
    if foodenumitem_with_same_identifier and foodenumitem_with_same_identifier["_id"] != foodenumitem.id:
        raise HTTPException(status_code=409, detail="A FoodEnumItem with the same identifier already exists")

    if foodenumitem.id:
        # Update existing FoodEnumItem
        old_foodenumitem = db.food_enum_items.find_one({"_id": foodenumitem.id})
        if not old_foodenumitem:
            raise HTTPException(status_code=404, detail="Existing FoodEnumItem not found")

        db.food_enum_items.update_one({"_id": foodenumitem.id}, {"$set": to_mongo(foodenumitem)})

    else:
        # Create new FoodEnumItem
        foodenumitem.id = db.food_enum_items.insert_one(to_mongo(foodenumitem)).inserted_id

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()

    return foodenumitem


@router.get("/foodenumitems", response_model=list[FoodEnumItem], description="Get a list of FoodEnumItem datasets")
async def get_foodenumitems(user: User = Depends(currentUser)):
    foodenumitems = db.food_enum_items.find()
    return [FoodEnumItem(**foodenumitem) for foodenumitem in foodenumitems]


@router.delete("/foodenumitem/{foodenumitem_uuid}", description="Delete a FoodEnumItem")
async def delete_foodenumitem(foodenumitem_uuid: PyObjectId, user: User = Depends(currentUser)):
    if user.role != Role.Administrator:
        raise HTTPException(status_code=403, detail="Only administrators are allowed to delete FoodEnumItems")

    foodenumitem = db.food_enum_items.find_one({"_id": foodenumitem_uuid})
    if not foodenumitem:
        raise HTTPException(status_code=404, detail="FoodEnumItem not found")

    db.food_enum_items.delete_one({"_id": foodenumitem_uuid})

    # Regenerate cache legacy schema because database schema was modified
    regenerate_legacy_schema()
