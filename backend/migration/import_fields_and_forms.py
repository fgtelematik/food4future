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
from typing import List

from models import db
from schema_v2.schema_models import preserved_identifiers


def find_by_identifier(identifier: str, data: List[dict]):
    if not identifier:
        return None

    for field in data:
        if field["identifier"] == identifier:
            return field

    return None


def replace_identifiers(data: List[dict]):
    for field in data:
        field["identifier"] = field["id"]
        del field["id"]


def import_enums():
    enums_file = open("schema/enums.json", 'r', encoding='utf8')
    enums_data = json.load(enums_file)["enums"]

    enums = []

    for enum_data in enums_data:
        if enum_data["id"] in preserved_identifiers:
            continue

        items = []
        enum_length = len(enum_data["element_ids"])

        for i in range(enum_length):
            item = {
                "identifier": enum_data["element_ids"][i],
                "label": enum_data["element_labels"][i]
            }
            items.append(item)

        enum = {
            "identifier": enum_data["id"],
            "items": items
        }

        # delete old enum, if it exists
        db.input_enum.delete_one({"identifier": enum["identifier"]})

        # insert new enum
        db.input_enum.insert_one(enum)

        enums.append(enum)

    print("Imported " + str(len(enums)) + " enums.")

    return enums


def import_fields(enums):
    schema_file = open("schema/schemas.json", 'r', encoding='utf8')
    schema_data = json.load(schema_file)["schemas"]

    replace_identifiers(schema_data)

    fields = []

    for field_data in schema_data:
        if field_data["identifier"] in preserved_identifiers:
            continue

        if (field_data["datatype"] == "EnumType" or
                field_data["datatype"] == "ListType" and field_data["elements_type"] == "EnumType"):
            enum = find_by_identifier(field_data["adt_enum_id"], enums)
            if enum is None:
                print("Warning: Enum with identifier %s not found." % field_data["adt_enum_id"])
            else:
                field_data["adt_enum_id"] = enum["_id"]

        if "permissions" in field_data:
            permissions = field_data["permissions"]
            field_data["permissions"] = []

            for permission in permissions:
                if permission["role"] == "all":  # convert "all" to individual permissions
                    for role in ['Nurse', 'Supervisor', 'Participant']:
                        field_data["permissions"].append({
                            "role": role,
                            "type": permission["type"]
                        })
                else:
                    field_data["permissions"].append(permission)

        # delete old field, if it exists
        db.input_field.delete_one({"identifier": field_data["identifier"]})

        # insert new field
        db.input_field.insert_one(field_data)

        fields.append(field_data)

    print("Imported " + str(len(fields)) + " fields.")

    return fields


def import_forms(fields):
    adt_file = open("schema/adts.json", 'r', encoding='utf8')
    adt_data = json.load(adt_file)

    forms = []

    for form_identifier, field_identifiers in adt_data.items():
        if form_identifier in preserved_identifiers:
            form_identifier = "study_" + form_identifier

        form = {
            "identifier": form_identifier,
            "title": form_identifier,
            "fields": []
        }

        for field_identifier in field_identifiers:
            if field_identifier in preserved_identifiers:
                continue

            field = find_by_identifier(field_identifier, fields)
            if field is None:
                print("Warning: Field with identifier %s not found on form %s." % field_identifier, form_identifier)
            else:
                form["fields"].append(field["_id"])

        # delete old form, if it exists
        db.input_form.delete_one({"identifier": form["identifier"]})

        # insert new form
        db.input_form.insert_one(form)

        forms.append(form)

    print("Imported " + str(len(adt_data)) + " forms.")

    return forms


def fix_form_ids(forms, fields):
    num_fixed = 0

    for field in fields:
        if field["datatype"] == "ADT" or "elements_type" in field and field["elements_type"] == "ADT":
            form = find_by_identifier(field["adt_enum_id"], forms)
            if form is None:
                print("Warning: Form with identifier %s not found." % field["adt_enum_id"])
            else:
                field["adt_enum_id"] = form["_id"]
                db.input_field.update_one({"_id": field["_id"]}, {"$set": {"adt_enum_id": form["_id"]}})
                num_fixed += 1

    print("Fixed " + str(num_fixed) + " subform ids.")


def main():
    print("Importing enums, fields and forms...")

    enums = import_enums()
    fields = import_fields(enums)
    forms = import_forms(fields)
    fix_form_ids(forms, fields)

    print("Done.")


if __name__ == "__main__":
    main()
