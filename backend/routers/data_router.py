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

from datetime import datetime
from typing import List

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import Response, StreamingResponse

from csv_generator import generate_csv
from routers import session
from data_extractors import extract_user_data, extract_sensor_data
from enums import Role, DataType
from models import StudyDataStats, PyObjectId, User, db, StudyDataStatsPerUser, StudyDataRequest, to_mongo, \
    StudyDataPerUser, StudyData, get_user_identifier
from utils import debug_print, sanitize_filename

router = APIRouter(
    prefix="/data/v2",
    tags=["Data Request (v2)"],
)


# due to privacy regulations, every data request will be logged
# this regards to both participant stats and participant data requests.
def log_data_request(target_user_ids: List[PyObjectId] | PyObjectId, current_user: User,
                     request: StudyDataRequest | None = None):
    if type(target_user_ids) != list:
        target_users = [target_user_ids]
    else:
        target_users = target_user_ids

    log_entry = {
        "time": datetime.utcnow(),
        "request_user": current_user.id,
        "target_users": target_users,
        "request": to_mongo(request)  # request object for data requests, None/null for stats requests
    }

    db.requests.create_index("time")
    db.requests.insert_one(log_entry)


def as_list(value):
    if value and type(value) != list:
        return [value]
    else:
        return value


def stats_for_user(user_id: PyObjectId, current_user: User, log_request: bool = True):
    target_user = db.user.find_one({"_id": user_id})
    if target_user is None:
        raise HTTPException(status_code=404, detail="User not found.")

    target_user = User(**target_user)

    if current_user.role != Role.Supervisor or target_user.study_id != current_user.study_id:
        raise HTTPException(status_code=403, detail="Operation not permitted.")
        # only supervisors may access data of users from their own study

    if log_request:
        log_data_request(user_id, current_user)

    res = list()

    # Generate Sensor Data Stats
    # ==========================
    sensor_data_types = db.sensor_data.distinct('type', {'user_id': user_id})
    for sensor_data_type in sensor_data_types:
        entry = dict()
        entry['data_type'] = DataType.SensorData
        entry['sensor_data_type'] = sensor_data_type
        entry['user_id'] = user_id

        query = {"user_id": user_id, "type": sensor_data_type}

        oldest_dataset = db.sensor_data.find_one(query, sort=[("timestamps", 1)])
        newest_dataset = db.sensor_data.find_one(query, sort=[("timestamps", -1)])

        entry['timestamp_first'] = None if oldest_dataset is None else min(oldest_dataset['timestamps'])
        entry['timestamp_last'] = None if newest_dataset is None else max(newest_dataset['timestamps'])

        # This aggregation pipeline first filters the data by query and next
        #  builds a new document (group), which contains the sum of all matched timestamp array sizes,
        # which describes the overall number of recorded samples for the specified user of the specified data type
        aggregation_pipeline = [
            {"$match": query},
            {"$group": {"_id": None, "totalSize": {"$sum": {"$size": "$timestamps"}}}}
        ]

        cur = db.sensor_data.aggregate(aggregation_pipeline)

        db_result_element = cur.next()
        if db_result_element is None:
            entry['num_datasets'] = 0
        else:
            entry['num_datasets'] = db_result_element["totalSize"]

        res.append(StudyDataStats(**entry))

    # Generate User Data Stats
    # ==========================
    entry = dict()
    entry['data_type'] = DataType.UserData
    entry['user_id'] = user_id

    query = {"user_id": user_id}

    num_datasets = db.user_data.count_documents(query)

    if num_datasets > 0:
        oldest_dataset = db.user_data.find_one(query, sort=[("effective_day", 1)])
        newest_dataset = db.user_data.find_one(query, sort=[("effective_day", -1)])

        entry['num_datasets'] = num_datasets

        entry['timestamp_first'] = None if oldest_dataset is None else int(
            oldest_dataset['effective_day'].timestamp() * 1000)
        entry['timestamp_last'] = None if newest_dataset is None else int(
            newest_dataset['effective_day'].timestamp() * 1000)

        res.append(StudyDataStats(**entry))

    # Generate Static Data Stats
    # ==========================
    if target_user.anamnesis_data is not None:
        entry = dict()
        entry['data_type'] = DataType.StaticData
        entry['user_id'] = user_id
        entry['num_datasets'] = 1
        entry['timestamp_first'] = None
        entry['timestamp_last'] = None

        res.append(StudyDataStats(**entry))

    return {user_id: res}


@router.get("/stats/{user_id}",
            response_model=StudyDataStatsPerUser,
            description="Get stats about which data is available for a specific participant user.")
def stats(user_id: PyObjectId, current_user: User = Depends(session.currentUser)):
    return stats_for_user(user_id, current_user)


@router.get("/stats",
            response_model=StudyDataStatsPerUser,
            description="List all participant users and stats about which data is available.")
def stats(current_user: User = Depends(session.currentUser)):
    if current_user.role != Role.Supervisor or not current_user.study_id:
        raise HTTPException(status_code=403, detail="Operation not permitted.")
        # only supervisors may access participant data

    target_users = db.user.find({"study_id": current_user.study_id, "role": Role.Participant.name})

    res = dict()

    for target_user in target_users:
        entries = stats_for_user(target_user["_id"], current_user, log_request=False)
        res[target_user["_id"]] = entries[target_user["_id"]]

    return res


@router.post("/request",
             response_model=StudyDataPerUser,
             description="Request collected Study Data.")
def request_data(request_params: StudyDataRequest, current_user: User = Depends(session.currentUser)):
    debug_print("request_data() called.")

    # Assert user role
    if current_user.role != Role.Supervisor:
        raise HTTPException(status_code=403, detail="Operation not permitted.")
        # only supervisors may access data

    target_user_ids = as_list(request_params.user_id)
    data_types = as_list(request_params.data_type)
    sensor_data_types = as_list(request_params.sensor_data_type)

    study_id_filter = {"study_id": current_user.study_id}
    if not current_user.study_id:
        study_id_filter = {}

    target_users = []

    for target_user_id in target_user_ids:
        target_user_data = db.user.find_one({"_id": target_user_id} | study_id_filter)
        if target_user_data:
            target_users.append(User(**target_user_data))
        else:
            raise HTTPException(status_code=404, detail="User with ID '" + str(target_user_id) +
                                                        "'not found or not assigned to your study.")

    log_data_request(target_user_ids, current_user, request_params)

    res = dict()

    for target_user in target_users:
        res_user = list()

        for data_type in data_types:

            if data_type == DataType.StaticData:
                debug_print("Extracting static data for user " + str(target_user.id) + ".")

                if target_user.anamnesis_data is not None:
                    res_user.append(StudyData(
                        data_type=data_type,
                        sensor_data_type=None,
                        count=1,
                        data=target_user.anamnesis_data if not request_params.count_only else None
                    ))
                continue

            if data_type == DataType.UserData:
                debug_print("Extracting user data for user " + str(target_user.id) + ".")

                data = extract_user_data(target_user.id,
                                         request_params.timestamp_first,
                                         request_params.timestamp_last,
                                         request_params.skip,
                                         request_params.limit)
                res_user.append(StudyData(
                    data_type=data_type,
                    sensor_data_type=None,
                    count=len(data),
                    data=data if not request_params.count_only else None
                ))
                continue

            if data_type == DataType.SensorData:
                sensor_data_types_user = sensor_data_types
                if sensor_data_types is None:
                    sensor_data_types_user = db.sensor_data.distinct('type', {'user_id': target_user.id})

                for sensor_data_type in sensor_data_types_user:
                    debug_print("Extracting sensor data of type '" + sensor_data_type + "' for user " + str(
                        target_user.id) + ".")

                    data = extract_sensor_data(
                        sensor_data_type,
                        target_user.id,
                        request_params.timestamp_first,
                        request_params.timestamp_last,
                        request_params.skip,
                        request_params.limit,
                        request_params.filter_duplicates
                    )

                    count = -1  # unknown number of data sets
                    if 'timestamps' in data:
                        count = len(data['timestamps'])

                    if not data or count == 0:
                        # either sensor_data_type not found or no data available
                        continue

                    res_user.append(StudyData(
                        data_type=data_type,
                        sensor_data_type=sensor_data_type,
                        count=count,
                        data=data if not request_params.count_only else None
                    ))

        res[target_user.id] = res_user

    debug_print("request_data() finished.")
    return res


@router.post("/request_csv",
             description="Request collected Study Data as CSV file.",
             response_description="CSV file with requested data.",
             response_class=StreamingResponse)
def request_data_csv(req_data: StudyDataPerUser = Depends(request_data), current_user=Depends(session.currentUser)):
    response = StreamingResponse(generate_csv(req_data))

    study_name = "study"

    if current_user.study_id:
        study = db.studies.find_one({"_id": current_user.study_id})
        if study and "title" in study:
            study_name = sanitize_filename(study["title"])

    user_part = ""
    if len(req_data.items()) == 1:
        user_id = next(iter(req_data))
        user_part = get_user_identifier(user_id) + "_"

    time_part = datetime.now().strftime("%Y-%m-%d_%H-%M-%S")

    file_name = study_name + "_" + user_part + time_part + ".csv"

    response.headers["Content-Disposition"] = 'attachment; filename="' + file_name + '"'  # TODO: Specify filename
    response.headers["Content-Type"] = 'text/csv'

    return response
