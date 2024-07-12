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
import re
from typing import List

from Cheetah.Template import Template
from fastapi import APIRouter, Depends, HTTPException, Request
from starlette.responses import HTMLResponse

import utils
from routers import session
from config import Config, ConfigParam
from models import db
from schema_v2.legacy_converter import regenerate_legacy_schema_sync
from schema_v2.schema_models import FoodImage, Study

legacy_schema_router = APIRouter(
    tags=["Internal - Schema Access (used by Android App)"])


def get_study(current_user=Depends(session.currentUser)) -> Study | None:
    study_id = current_user.study_id

    if study_id:
        study = db.studies.find_one({"_id": study_id})
        if study:
            return Study(**study)

    return None


def _get_legacy_schema(schema_fields: List[str]):
    # check if file exists: "schema_v2/cached_schemas.json":
    if not os.path.isfile("schema_v2/cached_schemas.json"):
        # if not, create it first
        regenerate_legacy_schema_sync()

    # Read dynamic schema from cache file.
    # This file is updated in background everytime the schema was modified
    # (triggered by schema_router and executed in legacy_converter.py)
    with open("schema_v2/cached_schemas.json", 'r', encoding='utf8') as schema_file:
        schema_data = json.load(schema_file)

    with open("schema_v2/predefined_input_schemas.json", 'r', encoding='utf8') as schema_file:
        predefined_schema_data = json.load(schema_file)

    schema_data["adts"] |= predefined_schema_data["adts"]
    schema_data["schemas"] += predefined_schema_data["schemas"]
    schema_data["enums"] += predefined_schema_data["enums"]

    res = {}

    for field in schema_fields:
        if field in schema_data:
            res[field] = schema_data[field]
        else:
            res[field] = None

    return res


@legacy_schema_router.get("/schemas")
async def schemas(study=Depends(get_study)):
    schema_data = _get_legacy_schema(["schemas"])

    for schema in schema_data["schemas"]:
        # Prevent app from refusing form generation when there is one label missing
        if "label" not in schema or schema["label"] is None:
            schema["label"] = ""

        # Inject default study runtime
        if (schema["id"] == "study_end_date" and
                isinstance(study.default_runtime_days, int) and
                study.default_runtime_days > 0):
            schema["defaultValue"] = "today+" + str(study.default_runtime_days)

    return schema_data


@legacy_schema_router.get("/enums")
async def enums(study=Depends(get_study)):
    enum_data = _get_legacy_schema(["enums", "enum_transitions"])

    enums_list = enum_data["enums"]
    enum_transitions = enum_data["enum_transitions"]

    # Try to replace the pre-defined enum with the identifier "FoodType" (which the app uses
    # as initial Food Screen) with the Initial Food Screen specified in the settings for the
    # study, the current user is assigned to:
    if study and study.initial_food_enum:
        init_food_enum = db.food_enums.find_one({"_id": study.initial_food_enum})

        if init_food_enum and "identifier" in init_food_enum and init_food_enum["identifier"]:
            init_food_enum_identifier = init_food_enum["identifier"]

            # Find generated legacy item for the Food Enum, which is set as initial
            init_food_enum_legacy = [item for item in enums_list if
                                     item["id"] == init_food_enum_identifier]
            if len(init_food_enum_legacy) != 1:
                raise HTTPException(status_code=500,
                                    detail="Missing initial FoodType enum '" + init_food_enum_identifier
                                           + "' in generated legacy enums.")
            init_food_enum_legacy = init_food_enum_legacy[0]

            # Find index of default Initial Food Enum (placeholder) defined in predefined_input_schemas.json
            predefined_foodtype = [idx for idx, enum_item in enumerate(enums_list) if enum_item["id"] == "FoodType"]
            if len(predefined_foodtype) != 1:
                raise HTTPException(status_code=500, detail="Missing default FoodType enum in"
                                                            " predefined_input_schemas.json. "
                                                            "Do you use an inconsistent git repo?")
            predefined_foodtype_index = predefined_foodtype[0]

            # Replace default Initial Food Enum (placeholder) with a copy of the Food Enum, which is set as initial
            enums_list[predefined_foodtype_index] = dict(init_food_enum_legacy)
            init_food_enum_legacy["id"] = "FoodType"  # make the app recognize this Food Enum as default screen.

            # Add transition for Initial Enum, if exists:
            if init_food_enum_identifier in enum_transitions:
                enum_transitions["FoodType"] = enum_transitions[init_food_enum_identifier]

    return enum_data


@legacy_schema_router.get("/adts")
async def adts(study=Depends(get_study)):
    legacy_adts = _get_legacy_schema(["adts"])["adts"]

    user_data_fields = ["no_user_data_fields"]
    static_data_fields = ["no_static_data_fields"]

    if study and study.user_data_form:
        user_data_form = db.input_form.find_one({"_id": study.user_data_form})

        if user_data_form and "identifier" in user_data_form and user_data_form["identifier"]:
            user_data_form_identifier = user_data_form["identifier"]

            if user_data_form_identifier not in legacy_adts:
                raise HTTPException(status_code=500,
                                    detail="Missing Initial Daily Questions Form '" + user_data_form_identifier
                                           + "' in generated legacy forms.")

            user_data_fields = legacy_adts[user_data_form_identifier]

    if study and study.static_data_form:
        static_data_form = db.input_form.find_one({"_id": study.static_data_form})

        if static_data_form and "identifier" in static_data_form and static_data_form["identifier"]:
            static_data_form_identifier = static_data_form["identifier"]

            if static_data_form_identifier not in legacy_adts:
                raise HTTPException(status_code=500,
                                    detail="Missing Initial Static Data Form '" + static_data_form_identifier
                                           + "' in generated legacy forms.")

            static_data_fields = legacy_adts[static_data_form_identifier]

    legacy_adts["anamnesis_data"] += static_data_fields
    legacy_adts["user_data"] += user_data_fields

    return legacy_adts


def _convert_time_string(time_string: str, default: str):
    if time_string is None or not re.match(r"^\d{2}:\d{2}$", time_string):
        return default

    return time_string.replace(":", "")


@legacy_schema_router.get("/config")
def get_config(apk_url=Depends(utils.get_apk_url), current_user=Depends(session.currentUser)):
    with open("schema/default_deviceconfig.json", 'r', encoding='utf8') as config_file:
        config_data = json.load(config_file)

    config_data["apk_download_url"] = apk_url
    config_data["apk_version_code"] = utils.get_apk_version_code()

    study = None
    if current_user.study_id:
        study = db.studies.find_one({"_id": current_user.study_id})

    if not study:
        return config_data

    # Read config from current study
    study = Study(**study)

    sensors_used = []

    config_data["food_input_reminder_time"] = _convert_time_string(study.reminder_time,
                                                                   config_data["food_input_reminder_time"])
    config_data["server_autosync_interval_minutes"] = study.server_autosync_interval
    config_data["server_sync_max_age_minutes"] = study.server_sync_max_age

    if study.cosinuss_device_config:
        if study.cosinuss_device_config.active:
            sensors_used.append("Cosinuss")

        config_data["cosinuss_wearing_reminder_time"] \
            = _convert_time_string(study.cosinuss_device_config.reminder_time,
                                   config_data["cosinuss_wearing_reminder_time"])
        config_data["cosinuss_wearing_time_begin"] \
            = _convert_time_string(study.cosinuss_device_config.wearing_time_begin,
                                   config_data["cosinuss_wearing_time_begin"])
        config_data["cosinuss_wearing_time_end"] \
            = _convert_time_string(study.cosinuss_device_config.wearing_time_end,
                                   config_data["cosinuss_wearing_time_end"])
        config_data["cosinuss_wearing_time_duration"] \
            = _convert_time_string(study.cosinuss_device_config.wearing_time_duration,
                                   config_data["cosinuss_wearing_time_duration"])

    if study.garmin_device_config:
        if study.garmin_device_config.active:
            sensors_used.append("Garmin")

        config_data["garmin_license_key"] = study.garmin_device_config.health_sdk_license_key
        config_data["sensor_autosync_interval_minutes"] = study.garmin_device_config.sensor_autosync_interval_minutes
        config_data["sensor_sync_max_age_minutes"] = study.garmin_device_config.sensor_autosync_interval_minutes

        logging_options = config_data["garmin_logging"]

        logging_options["BBI"]["enabled"] = study.garmin_device_config.bbi_enabled
        logging_options["ZERO_CROSSING"]["enabled"] = study.garmin_device_config.zero_crossings_enabled
        logging_options["ZERO_CROSSING"]["interval"] = study.garmin_device_config.zero_crossings_interval
        logging_options["STEPS"]["enabled"] = study.garmin_device_config.steps_enabled
        logging_options["STRESS"]["enabled"] = study.garmin_device_config.stress_enabled
        logging_options["STRESS"]["interval"] = study.garmin_device_config.stress_interval
        logging_options["HEART_RATE"]["enabled"] = study.garmin_device_config.heart_rate_enabled
        logging_options["HEART_RATE"]["interval"] = study.garmin_device_config.heart_rate_interval
        logging_options["SPO2"]["enabled"] = study.garmin_device_config.spo2_enabled
        logging_options["SPO2"]["interval"] = study.garmin_device_config.spo2_interval
        logging_options["RESPIRATION"]["enabled"] = study.garmin_device_config.respiration_enabled
        logging_options["RESPIRATION"]["interval"] = study.garmin_device_config.respiration_interval
        logging_options["ACCELEROMETER"]["enabled"] = study.garmin_device_config.raw_accelerometer_enabled
        logging_options["ACCELEROMETER"]["interval"] = study.garmin_device_config.raw_accelerometer_interval

    config_data["sensors_used"] = sensors_used

    return config_data


@legacy_schema_router.get("/images/sources.html", response_class=HTMLResponse)
async def get_image_sources(req: Request):
    with open("schema_v2/image_license_info.html", 'r', encoding='utf8') as templateFile:
        template_def = templateFile.read()

    food_images = db.food_images.find()
    food_images = [FoodImage(**food_image) for food_image in food_images]

    tpl = Template(template_def)
    tpl.food_images = food_images
    tpl.image_base_url = utils.get_base_url(req) + "images/"

    return str(tpl)
