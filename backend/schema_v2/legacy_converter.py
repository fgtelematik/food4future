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
import os
import threading
import time

from models import db

_generation_requested = False
_generation_running = False


def _remove_null_value_fields(data: dict):
    # We need to remove all fields with null values for Android App, which currently (APK Version 40) has problems with
    # None /null values - Explination follows:
    #  The checks for existing fields in the method
    #       de.thwildau.f4f.studycompanion.ui.customform.CustomFieldFactory.createCustomFieldFromSchema()
    #  currently only check the presence of a field property by checking for schema.has("{field}").
    #  If a field on a Pydantic Object is set to None here, the org.json.JSONObject interpreter
    #  on Android will parse this as an existing field with the string value "null", which is based
    #  on a original bug in org.json, adapted to Android SDK, see: https://stackoverflow.com/q/18226288/5106474
    #  In fact, the Android app should better check for schema.isNull("{field}"), to decide, whether an optional
    #  field parameter is present or not, but unfortunately this isn't the case  and we do not want
    #  to implement an additional update for the Android app in the current final project phase (2023-12).
    for key in list(data.keys()):
        if data[key] is None:
            del data[key]


def _get_converted_input_forms():
    forms_data = db["input_form"].find({})
    forms = {}

    for form in forms_data:
        field_list = []

        for field in form["fields"]:
            field_obj = db["input_field"].find_one({"_id": field})

            if not field_obj:
                continue

            field_list.append(field_obj["identifier"])

        forms[form["identifier"]] = field_list

    return forms


def _get_converted_input_fields():
    fields_data = db["input_field"].find({})
    fields = []

    for field in fields_data:
        del field["_id"]
        field["id"] = field["identifier"]
        del field["identifier"]

        if "adt_enum_id" in field and field["adt_enum_id"]:
            if (field["datatype"] == "EnumType" or
                    field["datatype"] == "ListType" and field["elements_type"] == "EnumType"):

                enum = db["input_enum"].find_one({"_id": field["adt_enum_id"]})
                if enum is None:
                    print("ERROR: Invalid Enum ID: " + str(field["adt_enum_id"]) + " in field: " + field["id"])
                    continue

                field["adt_enum_id"] = enum["identifier"]

            elif (field["datatype"] == "ADT" or
                  field["datatype"] == "ListType" and field["elements_type"] == "ADT"):

                form = db["input_form"].find_one({"_id": field["adt_enum_id"]})
                if form is None:
                    print("ERROR: Invalid Form ID: " + str(field["adt_enum_id"]) + " in field: " + field["id"])
                    continue

                field["adt_enum_id"] = form["identifier"]

        _remove_null_value_fields(field)

        # prevent division by zero in Android App
        if "useSlider" in field and field["useSlider"]:
            if "sliderStepSize" not in field or field["sliderStepSize"] <= 0:
                field["sliderStepSize"] = 1

        fields.append(field)

    return fields


def _get_converted_input_enums():
    enums_data = db["input_enum"].find({})
    enums = []

    for enum_data in enums_data:
        item_list = enum_data["items"]

        element_ids = []
        element_labels = []

        for item in item_list:
            element_ids.append(item["identifier"])
            element_labels.append(item["label"])

        enum = {
            "id": enum_data["identifier"],
            "element_ids": element_ids,
            "element_labels": element_labels,
        }

        enums.append(enum)

    return enums


def _convert_food_enum_transitions(transitions: dict, enum_identifier: str) -> dict:
    res = {}
    for trans in transitions:
        require_tags: list[str] = trans["require_tags"]
        add_tags: list[str] = trans["add_tags"]

        source = "*"
        if trans["selected_item_id"] is not None:
            selected_item = db["food_enum_items"].find_one({"_id": trans["selected_item_id"]})
            if selected_item is None:
                print("ERROR: Invalid Selected Item ID: " + str(
                    trans["selected_item_id"]) + " in enum: " + enum_identifier)
                continue
            source = selected_item["identifier"]

        target = None
        if trans["target_enum"] is not None:
            target_item = db["food_enums"].find_one({"_id": trans["target_enum"]})
            if target_item is None:
                print("ERROR: Invalid Target Enum ID: " + str(trans["target_enum"]) + " in enum: " + enum_identifier)
                continue
            target = target_item["identifier"]

        if len(require_tags) > 0:
            source += "+" + "+".join(require_tags)
        if target and len(add_tags) > 0:
            target += "+" + "+".join(add_tags)

        res[source] = target

    return res


def _get_converted_food_enums_and_transitions():
    # with open("schema/enums.json", 'r', encoding='utf8') as enum_file:
    #     static_enum_data = json.load(enum_file)
    #
    # enums = static_enum_data["enums"]
    #
    enums = []
    enum_data = db["food_enums"].find({})
    transitions = {}

    for enum in enum_data:

        element_ids = []
        element_labels = []
        element_explicit_labels = []
        element_image_urls = []

        contains_food_items = False

        any_images = False
        any_explicit_labels = False

        for item in enum["item_ids"]:
            item_obj = db["food_enum_items"].find_one({"_id": item})

            if not item_obj:
                continue

            element_ids.append(item_obj["identifier"])

            label = ""
            if "label" in item_obj and item_obj["label"] is not None:
                label = item_obj["label"]
            element_labels.append(label)

            image_filename = None
            if "image" in item_obj and item_obj["image"] is not None:
                image_obj = db["food_images"].find_one({"_id": item_obj["image"]})

                if image_obj is not None:
                    image_filename = "images/" + image_obj["filename"]
                    any_images = True
                else:
                    print("Warning: Invalid image reference for item %s" % item_obj["identifier"])

            element_image_urls.append(image_filename)

            if item_obj["is_food_item"]:
                contains_food_items = True

            if "explicit_label" in item_obj and item_obj["explicit_label"]:
                any_explicit_labels = True
                element_explicit_labels.append(item_obj["explicit_label"])
            else:
                element_explicit_labels.append(None)

        enum_obj = {
            "id": enum["identifier"],
            "element_ids": element_ids,
            "element_labels": element_labels,
        }

        if any_explicit_labels:
            enum_obj["element_explicit_labels"] = element_explicit_labels

        if any_images:
            enum_obj["element_image_urls"] = element_image_urls

        if "help_text" in enum and enum["help_text"] is not None:
            enum_obj["helpText"] = enum["help_text"]

        if "label" in enum and enum["label"] is not None:
            enum_obj["label"] = enum["label"]

        if contains_food_items:
            enum_obj["contains_food_items"] = True

        enums.append(enum_obj)

        if enum["transitions"] and len(enum["transitions"]) > 0:
            transitions[enum["identifier"]] = _convert_food_enum_transitions(enum["transitions"], enum["identifier"])

    return {"enums": enums, "enum_transitions": transitions}


def _get_converted_schema():
    schema_data = _get_converted_food_enums_and_transitions()
    schema_data["enums"] += _get_converted_input_enums()
    schema_data["schemas"] = _get_converted_input_fields()
    schema_data["adts"] = _get_converted_input_forms()

    return schema_data


def regenerate_legacy_schema_sync():
    global _generation_requested
    global _generation_running

    _generation_running = True
    _generation_requested = False

    print("Regenerating legacy schema...")

    try:
        # Do the work, can take a while
        schema_data = _get_converted_schema()
        schema_json = json.dumps(schema_data, indent=4, sort_keys=True, ensure_ascii=False).encode('utf8')

        # write result to a temp file first to minimize risk of parallel access to cached_schemas.json
        millis = str(int(round(time.time() * 1000)))
        filename = "cached_schemas-" + millis + ".json"
        with open("schema_v2/" + filename, 'wb') as schema_file:
            schema_file.write(schema_json)

        # rename the file to schemas.json and delete the old one
        if os.path.exists("schema_v2/cached_schemas.json"):
            os.remove("schema_v2/cached_schemas.json")
        os.rename("schema_v2/" + filename, "schema_v2/cached_schemas.json")

        print("Regenerating legacy schema done.")

    except Exception as e:
        print("ERROR while regenerating legacy schema: " + str(e))

    _generation_running = False

    if _generation_requested:
        _start_schema_regeneration()


def _start_schema_regeneration():
    threading.Thread(target=regenerate_legacy_schema_sync).start()


def regenerate_legacy_schema():
    global _generation_requested
    global _generation_running

    if _generation_running:
        _generation_requested = True
        return

    _generation_running = True
    _start_schema_regeneration()
