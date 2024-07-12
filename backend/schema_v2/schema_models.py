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

from typing import Optional, List

from pydantic import Field

from enums import Role
from models import LocalizedStr, PyObjectId, BaseModel, BaseModelNoId
from schema_v2.schema_enums import FieldType, PermissionType

# This list contains all identifiers, which are reserved for the system
# and cannot be used for fields, forms, input enums or food enums
preserved_identifiers = [
    # Forms
    "user_data",
    "user",
    "anamnesis_data",

    # Fields
    "study_begin_date",
    "study_end_date",
    "effective_day",
    "foodlist",
    "creation_time",
    "modification_time",
    "last_sync_id",
    "id",
    "username",
    "email",
    "new_password",
    "hsz_identifier",
    "containerBlank",
    "role",
    "no_user_data_fields",
    "no_static_data_fields",

    # Enums
    "role",
    "SensorDataType",
    "FoodType"
]


class Permission(BaseModelNoId):
    role: Role
    type: PermissionType


class InputEnumItem(BaseModelNoId):
    identifier: str
    label: LocalizedStr


class InputEnum(BaseModel):
    identifier: str
    items: List[InputEnumItem]


class InputForm(BaseModel):  # former "adt"
    identifier: str  # replaces "id" in schema >=0.5
    title: Optional[LocalizedStr] = None
    description: Optional[LocalizedStr] = None
    fields: List[PyObjectId]


class InputField(BaseModel):  # former "field"
    identifier: str  # replaces "id" in schema >=0.5 since the same name can be defined for multiple studies
    label: Optional[LocalizedStr] = Field(description="Field label", default=None)
    helpText: Optional[LocalizedStr] = Field(description="Additional description text", default=None)
    datatype: FieldType = Field(description="Field type")
    elements_type: Optional[FieldType] = Field(description="Type of the elements, if datatype is ListType",
                                               default=None)
    adt_enum_id: Optional[PyObjectId] = Field(description="Enum or ADT identifier of this field, "
                                                          "if datatype is Enum or ADT / Enum or ADT identifier "
                                                          "of the list elements Enum or ADT, if datatype is "
                                                          "ListType and elements_type is Enum or ADT", default=None)
    defaultValue: int | str | float | bool | list | dict | None = Field(description="Default value", default=None)
    minValue: Optional[float] = None
    maxValue: Optional[float] = None
    useSlider: Optional[bool] = Field(
        description="if true, value is selected via Slider instead of an text input field", default=None)
    sliderMinLabel: Optional[str] = Field(description="Label for the left end of the slider", default=None)
    sliderMaxLabel: Optional[str] = Field(description="Label for the right end of the slider", default=None)
    sliderStepSize: Optional[float] = Field(
        description="If neither 0 or null, slider lets choose discrete values with the specified step size",
        default=None)
    unitString: Optional[str] = Field(description="Unit string to be displayed after the input field", default=None)
    qrCodeInput: Optional[bool] = Field(
        description="Shows a button, which allows Input via scanned QR/Barcode. Only for StringType, "
                    "FloatType, IntType and no unitString specified.",
        default=None)
    displayPeriodDays: Optional[int] = Field(
        description="Setting this field to an int value n will display this field in a generated form only, "
                    "if the ‘effective_date’ field is set to a day, which is a multiple of n days started counting "
                    "from study_begin_date or started counting the day after study_begin_date, if displayDayOne is set.",
        default=None)
    displayDayOne: Optional[bool] = Field(
        description="If true, displayPeriodDays is counted from the day after study_begin_date, otherwise from "
                    "study_begin_date. Setting this field to true will also show the field when the ‘effective_date’ "
                    "refers to same day as ‘study_begin_date’",
        default=True)
    maybeNull: Optional[bool] = Field(description="If true, the field is not required to be filled out", default=None)
    permissions: Optional[List[Permission]] = Field(description="List of permissions for this field", default=None)


class FoodImage(BaseModel):
    filename: Optional[str] = None
    label: Optional[LocalizedStr] = None
    licenseUrl: Optional[str] = None
    licenseName: Optional[str] = None
    sourceInfo: Optional[str] = None
    sourceUrl: Optional[str] = None


class FoodEnumItem(BaseModel):
    # This can be either a food category, a food item or a food quantity or anything else selectable in the foodlist
    identifier: str
    label: LocalizedStr
    explicit_label: Optional[LocalizedStr] = None
    image: Optional[PyObjectId] = None
    is_food_item: bool


class FoodEnumTransition(BaseModel):
    require_tags: List[str]
    add_tags: List[str]
    selected_item_id: Optional[PyObjectId] = None
    target_enum: Optional[PyObjectId] = None


class FoodEnum(BaseModel):
    identifier: str
    label: LocalizedStr
    help_text: Optional[LocalizedStr] = None
    item_ids: List[PyObjectId]
    transitions: List[FoodEnumTransition]


class GarminDeviceConfig(BaseModelNoId):
    active: bool = Field(description="If true, garmin device will be used for data collection")
    health_sdk_license_key: str = Field(description="License key for the Garmin Health SDK")
    sensor_autosync_interval_minutes: int = Field(description="Interval in minutes the app will request downloading "
                                                              "logging data from the Garmin device.")
    sensor_sync_max_age_minutes: int = Field(description="Maximum age in minutes of the sensor data, before the user"
                                                         "will be notified to sync the device.")
    zero_crossings_enabled: bool = Field(description="If true, the zero crossings of the accelerometer axes will be "
                                                    "measured and logged.")
    zero_crossings_interval: int = Field(description="Interval in seconds, in which the zero crossings will be "
                                                     "measured and logged.")
    steps_enabled: bool = Field(description="If true, the step counter will be enabled and number of steps are logged.")
    stress_enabled: bool = Field(description="If true, the stress level will be measured and logged.")
    stress_interval: int = Field(description="Interval in seconds, in which the stress level will be measured and "
                                             "logged.")
    heart_rate_enabled: bool = Field(description="If true, the heart rate will be measured and logged.")
    heart_rate_interval: int = Field(description="Interval in seconds, in which the heart rate will be measured and "
                                                 "logged.")
    bbi_enabled: bool = Field(description="If true, the BBI (beat-to-beat-interval) will be measured and logged.")
    spo2_enabled: bool = Field(description="If true, the blood oxygen saturation will be measured and logged.")
    spo2_interval: int = Field(description="Interval in seconds, in which the blood oxygen saturation will be "
                                           "measured and logged.")
    respiration_enabled: bool = Field(description="If true, the respiration rate will be measured and logged.")
    respiration_interval: int = Field(description="Interval in seconds, in which the respiration rate will be "
                                                  "measured and logged.")
    raw_accelerometer_enabled: bool = Field(description="If true, the raw accelerometer data will be measured and "
                                                        "logged.")
    raw_accelerometer_interval: int = Field(description="Interval in seconds, in which the raw accelerometer data "
                                                        "will be measured and logged.")


class CosinussDeviceConfig(BaseModelNoId):
    active: bool = Field(description="If true, cosinuss° One device will be used for data collection")
    wearing_time_begin: str = Field(description="Begin of the time period of the day, within the cosinuss° One "
                                                "device should be put on. Format: HH:MM")
    wearing_time_end: str = Field(description="End of the time period of the day, within the cosinuss° One "
                                              "device should be put on. Format: HH:MM")
    wearing_time_duration: str = Field(description="Time duration, for which the cosinuss° One "
                                                   "device should be continuously put on. Format: HH:MM")
    reminder_time: str = Field(description="Time of the day when the reminder should be sent, if the device "
                                           "was not put on yet. Format: HH:MM")


class EmailConfig(BaseModelNoId):
    participant_subject: LocalizedStr
    participant_body_template: LocalizedStr \
        = Field(description="Body Template of the registration mail sent to the participant. "
                            "Placeholders: %apk_url%, %username%, %password%, %nopassword:{no password info text}%")
    nurse_subject: LocalizedStr
    nurse_body_template: LocalizedStr \
        = Field(description="Body Template of the registration mail sent to the staff. "
                            "Placeholders: %apk_url%, %username%, %password%, %nopassword:{no password info text}%")
    scientist_subject: LocalizedStr
    scientist_body_template: LocalizedStr \
        = Field(description="Body Template of the registration mail sent to the scientist. "
                            "Placeholders: %backend_url%, %username%, %password%, %nopassword:{no password info text}%")

class Study(BaseModel):
    title: LocalizedStr
    default_runtime_days: int = Field(description="Default runtime of the study in days.")
    initial_food_enum: Optional[PyObjectId] = None
    static_data_form: Optional[PyObjectId] \
        = Field(description="ID of the Form collecting the fields for the static participant data", default=None)
    user_data_form: Optional[PyObjectId] \
        = Field(description="ID of the Form collecting the fields for the daily question data", default=None)
    reminder_time: Optional[str] = Field(description="Time of the day when the reminder should be sent"
                                                     "if the daily questions were not answered yet. Format: HH:MM")
    server_autosync_interval: int = Field(description="Interval in minutes the app will trigger a background"
                                                      "data sync with the server, if the client has a WiFi connection.")
    server_sync_max_age: int = Field(description="Maximum age in minutes of the server data, before the user"
                                                 "will be notified to sync the data.")
    email_config: EmailConfig
    garmin_device_config: Optional[GarminDeviceConfig] = Field(description="Garmin Device configuration for this study",
                                                               default=None)
    cosinuss_device_config: Optional[CosinussDeviceConfig] = Field(description="cosinuss° One Device configuration for "
                                                                               "this study", default=None)
