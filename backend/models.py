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

import datetime
import urllib.parse
from typing import Optional, Union, Dict, List, Any, Annotated

from bson import ObjectId
from pydantic import BaseModel as PydanticBaseModel, Field, ConfigDict, RootModel, AfterValidator
from pydantic_core import core_schema
from pymongo import MongoClient
from sshtunnel import SSHTunnelForwarder

from config import Config, ConfigParam
from enums import Role, DataType

LocalizedStr = Union[str, Dict[str, str]]

tunnelServer = None


def connectMongoDB():
    global tunnelServer

    dbCredentials = ""

    dbUser = Config.getValue(ConfigParam.DbUser)
    if dbUser != None:
        dbUser = urllib.parse.quote_plus(dbUser)
        dbCredentials = dbUser

    dbPassword = Config.getValue(ConfigParam.DbPassword)
    if dbUser != None:
        dbPassword = urllib.parse.quote_plus(dbPassword)
        dbCredentials = '%s:%s' % (dbCredentials, dbPassword)

    if dbUser != None:
        dbCredentials = dbCredentials + "@"

    dbHost = Config.getValue(ConfigParam.DbHost)
    dbPort = Config.getValue(ConfigParam.DbPort)
    if dbPort != None:
        dbHost = dbHost + ":" + str(dbPort)

    dbAuthSource = Config.getValue(ConfigParam.DbAuthSource)
    if dbAuthSource == None:
        dbQueryParams = ""
    else:
        dbQueryParams = "?authSource=" + dbAuthSource

    sshHost = Config.getValue(ConfigParam.DbSSHTunnelHost)
    sshUser = Config.getValue(ConfigParam.DbSSHTunnelUser)
    sshPassword = Config.getValue(ConfigParam.DbSSHTunnelPassword)
    if Config.getValue(ConfigParam.DbSSHTunnelActive):
        if dbPort == None:
            dbPort = 27017  # mongodb default port
        tunnelServer = SSHTunnelForwarder(
            sshHost,
            ssh_username=sshUser,
            ssh_password=sshPassword,
            remote_bind_address=(Config.getValue(ConfigParam.DbHost), dbPort)
        )

        tunnelServer.start()
        dbHost = "127.0.0.1:" + str(tunnelServer.local_bind_port)  # auto-assigned local bind port for tunnel

    mongoUri = 'mongodb://%s%s/%s' % (dbCredentials, dbHost, dbQueryParams)

    client = MongoClient(mongoUri)

    db = client.get_database(Config.getValue(ConfigParam.DbName))

    # Do a test query to validate connection and authentication.
    # An exception will be thrown if connection/authentication is invalid
    db.test.find_one({})

    return db


db = connectMongoDB()


# Dataset conversions
# ===================

# find and convert all UTCTime strings in a dict and its subdicts into datetime objects
def parseUTCStrings(data: dict):
    for key in data:
        val = data[key]

        if isinstance(val, dict):
            data[key] = parseUTCStrings(data[key])  # recursively parse subdicts

        if isinstance(val, str):
            try:
                d = datetime.datetime.fromisoformat(val)
                data[key] = d
            except ValueError:
                pass
    return data


def convertMongoToJSON(data):
    data['id'] = str(data['_id'])
    del data['_id']
    if 'created_by' in data:
        data['created_by'] = str(data['created_by'])
    return True


def convertJSONToMongo(data):
    data = parseUTCStrings(data)
    if 'id' not in data or not ObjectId.is_valid(data['id']):
        return False
    data['_id'] = ObjectId(data['id'])
    del data['id']

    if 'created_by' in data and type(data['created_by']) is not ObjectId:
        data['created_by'] = ObjectId(data['created_by'])
    return True


# find some more details about this implementation of PyObjectId in my StackOverflow answer at:
# https://stackoverflow.com/a/77105412/5106474
class PyObjectId(str):
    @classmethod
    def __get_pydantic_core_schema__(
            cls, _source_type: Any, _handler: Any
    ) -> core_schema.CoreSchema:
        return core_schema.json_or_python_schema(
            json_schema=core_schema.str_schema(),
            python_schema=core_schema.union_schema([
                core_schema.is_instance_schema(ObjectId),
                core_schema.chain_schema([
                    core_schema.str_schema(),
                    core_schema.no_info_plain_validator_function(cls.validate),
                ])
            ]),
            serialization=core_schema.plain_serializer_function_ser_schema(
                lambda x: str(x)
            ),
        )

    @classmethod
    def validate(cls, value) -> ObjectId:
        if not ObjectId.is_valid(value):
            raise ValueError("Invalid ObjectId")

        return ObjectId(value)


base_config = ConfigDict(
    arbitrary_types_allowed=True,
    populate_by_name=True,
    json_encoders={
        ObjectId: str,
        datetime.date: lambda d: None if not d else datetime.combine(d.date(), datetime.time.min).isoformat(),
        datetime.datetime: lambda dt: None if not dt else dt.isoformat()
    }
)


class BaseModelNoId(PydanticBaseModel):
    model_config = base_config


class BaseModel(BaseModelNoId):
    id: Optional[PyObjectId] = Field(validation_alias='_id', default=None)


def to_mongo(obj, exclude: set[str] | None = None):
    if exclude is None:
        exclude = {'id'}

    if isinstance(obj, list):
        return [to_mongo(item, set()) for item in obj]

    if isinstance(obj, PydanticBaseModel):
        db_obj = obj.model_dump(exclude=exclude)

        for key in db_obj.keys():
            py_field_val = getattr(obj, key)

            if isinstance(py_field_val, ObjectId):
                db_obj[key] = ObjectId(py_field_val)
            else:
                db_obj[key] = to_mongo(py_field_val, set())

        return db_obj

    return obj


class LegacyResult(BaseModelNoId):
    success: bool


class Session(BaseModel):
    user: str
    hashed_token: str

    class Config:
        arbitrary_types_allowed = True
        json_encoders = {
            ObjectId: str
        }


class UserBase(BaseModel):
    username: str
    role: Role
    client_version: Optional[int] = 0
    study_id: Optional[PyObjectId] = None
    anamnesis_data: Optional[dict] = None
    hsz_identifier: Optional[str] = ""  # DEPRECATED! Use id as part of anamnesis_data instead!


class User(UserBase):
    password_hash: str
    salt: str = ""


class ResendMailRequest(BaseModel):
    email: str
    reset_password: bool = False


class UserInput(UserBase):
    new_password: Optional[str] = None
    email: Optional[str] = None

    # send_registration_mail field is kept for backwards compatibility and no longer used.
    # A registration email will always be sent if the email property is set.
    send_registration_mail: bool = False


class UserOutput(UserBase):
    created_by: Optional[PyObjectId] = None  # unset only for initial admin user
    creation_date: datetime.datetime = None


class LegacyUserOutput(PydanticBaseModel):
    user: UserOutput


class LegacyUsersOutput(PydanticBaseModel):
    users: List[UserOutput]


class UserCreationResult(BaseModel):
    username: str


class StudyDataStats(BaseModelNoId):
    data_type: DataType = Field(description="The data type for which the stats are requested.")
    timestamp_first: Optional[int] = Field(
        description="The UNIX timestamp in ms of the first available dataset (if any).",
        default=None)
    timestamp_last: Optional[int] = Field(
        description="The UNIX timestamp in ms of the last available dataset (if any).",
        default=None)
    num_datasets: int = Field(
        description="The number of available datasets for the given user and the given data type.")
    sensor_data_type: Optional[str] = Field(description="The specific type of sensor data, if data_type is SensorData",
                                            default=None)


StudyDataStatsPerUser = RootModel[Dict[PyObjectId, List[StudyDataStats]]]


class StudyDataRequest(BaseModelNoId):
    user_id: PyObjectId | List[PyObjectId] \
        = Field(description="The user ID for which the data is requested. If a list of user IDs is given, "
                            "the data of all users is returned.")
    data_type: DataType | List[DataType]
    sensor_data_type: Optional[str] | Optional[List[str]] \
        = Field(description="The specific type(s) of sensor data, if data_type is or contains SensorData."
                            "If data_type is SensorData and sensor_data_type is not set, all available"
                            "sensor data types will be collected.", default=None)
    timestamp_first: Optional[int] \
        = Field(description="Only request data sets effectively dated at the given UNIX timestamp in ms or "
                            "later. If unset, the timestamp of the oldest available dataset is used.",
                default=None)

    timestamp_last: Optional[int] \
        = Field(description="Only request data sets effectively dated at the given UNIX timestamp in ms or "
                            "earlier. If unset, the timestamp of the newest available dataset is used.",
                default=None)
    count_only: bool = Field(description="If true, only the number of available datasets is returned.",
                             default=False)
    skip: Optional[int] = Field(description="Skip the first n datasets.", default=None)
    limit: Optional[int] = Field(description="Limit the number of returned datasets.", default=None)
    filter_duplicates: Optional[bool] = Field(description="if set, duplicate values are filtered out before sending the"
                                                          "data. This reduces performance significantly.",
                                              default=False)


class StudyData(BaseModelNoId):
    data_type: DataType = Field(description="The data type of this data set.")
    sensor_data_type: Optional[str] = Field(description="The specific type of sensor data, if data_type is SensorData",
                                            default=None)
    count: int = Field(description="The number of data entries in this data set.")
    data: Dict | List[Dict] = Field(description="The data set itself. The format depends on the data type."
                                                "For SensorData, this is a Dict defining multiple sensor-individual"
                                                "list fields such as timestamps, values, etc.\n"
                                                "For StaticData, this is a study-individual data set.\n"
                                                "For UserData, this is a list of study-individual data sets.\n")


StudyDataPerUser = RootModel[Dict[PyObjectId, List[StudyData]]]


def get_user_identifier(user_id: PyObjectId):
    user_data = db.user.find_one({"_id": user_id})

    user = User(**user_data)

    if not user:
        return str(user_id)

    if user.hsz_identifier:
        return user.hsz_identifier

    if user.anamnesis_data:
        id_field = next((key for key in user.anamnesis_data if "identifier" in key.lower()), None)

        if not id_field:
            id_field = next((key for key in user.anamnesis_data if "id" in key.lower()), None)

        if id_field:
            identifier = user.anamnesis_data.get(id_field)
            if identifier and isinstance(identifier, str):
                return identifier

    if user.username:
        return user.username

    return str(user.id)
