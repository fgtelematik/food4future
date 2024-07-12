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
import re
from datetime import datetime

from bson import ObjectId
from fastapi import Depends, HTTPException, Request, APIRouter

from enums import Role
from models import db, User
from routers import session

router = APIRouter(
    prefix="/data",
    tags=["Data Request (v1 - deprecated)"],
)

# IMPORTANT NOTE:
# ===============
# This implementation has a lot of bad structure and formatting and the interface neither properly typed nor documented.
# Therefore it is deprecated and was re-implemented in data_router.py served at endpoint /data/v2
# but kept for backwards compatibility.

def filterFieldsByPermission(dataset: dict, user: User, writeAccess: bool = False,
                             alwaysIncludeId: bool = False,
                             schemaDefinitions: list = None):
    if user.role == Role.Administrator:
        # Administrator may always see all fields. The unfiltered original dataset is returned
        return dataset

    if schemaDefinitions == None:
        # read schema definitions from schema file, if not specified
        # used to avoid redundant file access on recursive call
        with open("schema/schemas.json", 'r', encoding='utf8') as schema_file:
            schemaDefinitions = json.load(schema_file)['schemas']

    res = dataset.copy()

    for key in dataset:

        if key == 'id' and alwaysIncludeId:
            continue

        fieldschema = None

        # find field  key in schemaDefinitions
        for schema in schemaDefinitions:
            if schema['id'] == key:
                fieldschema = schema
                break

        if fieldschema == None or 'permissions' not in fieldschema:
            # no field schema available or no permissions defined for this field
            del res[key]
            continue

        hasAccessForField = False

        # Check current user's role is permitted to have read access (or write acces,s if writeAccess == True)
        # for the specific field
        for permission in fieldschema['permissions']:
            if permission['role'] != user.role:
                continue

            if permission['type'] == 'Edit' or writeAccess == False and permission['type'] == 'Read':
                hasAccessForField = True
                break

        if not hasAccessForField:
            del res[key]
            continue

        if type(res[key]) == dict:
            # apply filtering recursively for subfields
            res[key] == filterFieldsByPermission(res[key], user, writeAccess, alwaysIncludeId, schemaDefinitions)

    return res


def extendLists(data: dict, data_to_extend: dict):
    for key in data_to_extend:

        # check data conformity
        if type(data_to_extend[key]) != list or key not in data or type(data[key]) != list:
            continue

        data[key].extend(data_to_extend[key])


def truncateLists(data: dict, n_head_elements: int, n_tail_elements: int):
    for key in data:
        list_mod = data[key]

        if type(list_mod) == list:

            if n_head_elements and n_head_elements > 0:
                # truncate start of list
                list_mod = list_mod[n_head_elements:]

            if n_tail_elements and n_tail_elements > 0:
                # truncate end of list
                list_mod = list_mod[:-n_tail_elements]

            data[key] = list_mod


def filterSensorData(sensor_data: dict, timestamp_first: int, timestamp_last: int, extracted_timestamps: set):
    # first, reduce dataset to effective sensor data (listable entries only)
    effective_sensor_data = dict()
    for key in sensor_data:
        if type(sensor_data[key]) == list:
            effective_sensor_data[key] = sensor_data[key]

    # initialize resulting dataset
    res = dict()
    for key in effective_sensor_data:
        res[key] = list()

    # search for data indices for which the timestamp is in range
    timestamps = effective_sensor_data['timestamps']
    for i in range(len(timestamps)):
        t = timestamps[i]
        duplicate = False

        if extracted_timestamps:
            duplicate = t in extracted_timestamps

        if t >= timestamp_first and t <= timestamp_last and not duplicate:
            # timestamp is in range.
            # Copy the corresponding effective data of all lists into resulting dataset
            for key in res:
                res[key].append(effective_sensor_data[key][i])

    return res


def determineAmount(user_dataset: dict):
    amount_value: float | None = None
    for key in user_dataset:
        if not re.search("amount", key, re.IGNORECASE):
            continue

        val = user_dataset[key]

        if type(val) == int or type(val) == float:
            amount_value = val

        if not user_dataset[key] or type(user_dataset[key]) != str:
            continue


def extractUserData(user_id: ObjectId, timestamp_first: int, timestamp_last: int, skip: int, limit: int):
    # since we only have user data concerning whole days,
    # we make timestamp_first be midnight of its referred day (00:00)
    timestamp_first = int(timestamp_first / 86400) * 86400
    # and make timestamp_last be the midnight of the day following its referred day
    timestamp_last = int(timestamp_last / 86400) * 86400 + 86400
    # Note: 24 hours have 86400 seconds.

    time_start = datetime.fromtimestamp(timestamp_first)
    time_end = datetime.fromtimestamp(timestamp_last)

    req = {'user_id': user_id, "effective_day": {"$gte": time_start, "$lte": time_end}}
    cur = db.user_data.find(req)

    if skip and skip > 0:
        cur = cur.skip(skip)

    if limit and limit > 0:
        cur = cur.limit(limit)

    res = []
    internal_keys = ['last_sync_id', 'last_sync_id_download', 'user_id', '_id', 'id']

    for dataset in cur:
        # remove server-internal information from dataset
        for filter_key in internal_keys:
            if filter_key in dataset:
                del dataset[filter_key]

        # Convert effective day to unix timestamp
        dataset['effective_day'] = int(
            datetime.combine(dataset['effective_day'].date(), datetime.min.time()).timestamp())

        # append corresponding dataset to result list
        res.append(dataset)

    return res


def extractSensorData(datatype: str, user_id: ObjectId, timestamp_first: int, timestamp_last: int, skip: int,
                      limit: int, filter_duplicates: bool):
    # generate request
    # this request might find multiple sensor data sets, which most likely contain data, which is also outside the specified time range
    req = {'type': datatype, 'user_id': user_id, 'timestamps': {'$gte': timestamp_first, '$lte': timestamp_last}}
    cur = db.sensor_data.find(req)
    res = None
    timestamps = None

    num_skipped = 0
    count = 0

    for dataset in cur:
        filtered_dataset = filterSensorData(dataset, timestamp_first, timestamp_last, timestamps)
        num_elements = len(filtered_dataset['timestamps'])

        if num_elements == 0:
            continue

        # skip heading datasets or elements of current dataset
        if skip and skip > 0:
            skipdiff = skip - num_elements - num_skipped
            if skipdiff >= 0:
                # whole current dataset is skipped
                num_skipped = num_skipped + num_elements
                continue
            else:
                # some heading elements of the current dataset might be truncated due to skip

                # numbers of heading elements of current data set to truncate:
                num_trunc_left = num_elements + skipdiff
                # (might be 0 if num_dataset is divisor of skip, but truncateLists does nothing in this case)

                truncateLists(filtered_dataset, num_trunc_left, None)
                num_elements = num_elements - num_trunc_left

                # Skipping has completed
                skip = False

        # limit maximum number of elements
        if limit and limit > 0:

            limitdiff = limit - count - num_elements

            if limitdiff == -num_elements:
                break  # limit was already reached in last iteration

            if limitdiff < 0:
                # tuncate the tail elements which would exceed the limit
                truncateLists(filtered_dataset, None, -limitdiff)
                num_elements = num_elements + limitdiff

        if res == None:
            res = filtered_dataset

            if filter_duplicates:
                timestamps = set(filtered_dataset['timestamps'])
        else:
            extendLists(res, filtered_dataset)

            if filter_duplicates:
                timestamps = timestamps.union(set(filtered_dataset['timestamps']))

        count = count + num_elements

    if res == None:
        res = dict()  # return empty dict, if no matched data was found

    return res


# due to privacy regulations, every data request will be logged
# this regards to both participant stats and participant data requests.
def recordRequest(target_user_ids, current_user: User, request: dict = None):
    if type(target_user_ids) != list:
        target_user_ids = [target_user_ids]
    target_users = []
    for target_user in target_user_ids:
        if type(target_user) == str and ObjectId.is_valid(target_user):
            target_user = ObjectId(target_user)

        target_users.append(target_user)

    if len(target_users) == 1:
        target_users = target_users[0]

    log_entry = {
        "time": datetime.utcnow(),
        "request_user": current_user.id,
        "target_user": target_users,
        "request": request  # request object for data requests, None/null for stats requests
    }

    db.requests.create_index("time")
    db.requests.insert_one(log_entry)


@router.get("/stats/{user_id}")
def handleStatsRequest(user_id: str, current_user: User = Depends(session.currentUser)):
    # Assert user role
    if current_user.role != Role.Supervisor:
        raise HTTPException(status_code=403, detail="Operation not permitted.")
        # only supervisors may access data

    user_id_str = user_id

    try:
        user_id = ObjectId(user_id)
    except:
        raise HTTPException(status_code=400, detail="Invalid format for user_id.")

    recordRequest(user_id, current_user)

    res = list()

    # Generate Sensor Data Stats
    # ==========================
    sensor_data_types = db.sensor_data.distinct('type', {'user_id': user_id})
    for sensor_data_type in sensor_data_types:
        stats = dict()
        stats['datatype'] = sensor_data_type
        stats['user_id'] = user_id_str

        query = {"user_id": user_id, "type": sensor_data_type}

        oldestDataset = db.sensor_data.find_one(query, sort=[("timestamps", 1)])
        newestDataset = db.sensor_data.find_one(query, sort=[("timestamps", -1)])

        stats['timestamp_first'] = None if oldestDataset == None else min(oldestDataset['timestamps'])
        stats['timestamp_last'] = None if newestDataset == None else max(newestDataset['timestamps'])

        # This aggregation pipeline first filters the data by query and next
        #  builds a new document (group), which contains the sum of all matched timestamp array sizes,
        # which describes the overall number of recorded samples for the specified user of the specified data type
        aggregationPipeline = [
            {"$match": query},
            {"$group": {"_id": None, "totalSize": {"$sum": {"$size": "$timestamps"}}}}
        ]

        cur = db.sensor_data.aggregate(aggregationPipeline)

        dbResultElement = cur.next()
        if dbResultElement == None:
            stats['num_datasets'] = 0
        else:
            stats['num_datasets'] = dbResultElement["totalSize"]

        res.append(stats)

    # Generate User Data Stats
    # ==========================
    stats = dict()
    stats['datatype'] = "UserData"
    stats['user_id'] = user_id_str

    query = {"user_id": user_id}

    oldestDataset = db.user_data.find_one(query, sort=[("effective_day", 1)])
    newestDataset = db.user_data.find_one(query, sort=[("effective_day", -1)])

    stats['num_datasets'] = db.user_data.count_documents(query)

    stats['timestamp_first'] = None if oldestDataset == None else int(oldestDataset['effective_day'].timestamp())
    stats['timestamp_last'] = None if newestDataset == None else int(newestDataset['effective_day'].timestamp())

    res.append(stats)

    return {'stats': res}


@router.post("/request")
def handleDataRequest(request: dict, http_request: Request, current_user: User = Depends(session.currentUser)):
    # Assert user role
    if current_user.role != Role.Supervisor:
        raise HTTPException(status_code=403, detail="Operation not permitted.")
        # only supervisors may access data

    # Assert input object fields

    # data_request = request['request']
    data_request = request

    # check for all required fields specified
    for key in ['user_id', 'datatype', 'timestamp_first', 'timestamp_last']:
        if key not in data_request:
            raise HTTPException(status_code=400, detail="Missing input parameter: " + key)

    # check for valid user_id and convert to list if only passed one user id
    user_ids = data_request['user_id']
    if type(user_ids) == str:
        user_ids = [user_ids]
    elif type(user_ids) != list:
        raise HTTPException(status_code=400, detail="Invalid type for user_id. Must be string or list of strings.")

    # check if timestamps are int numbers
    timestamp_first = data_request['timestamp_first']
    timestamp_last = data_request['timestamp_last']

    if type(timestamp_first) != int:
        raise HTTPException(status_code=400,
                            detail="Invalid type for timestamp_first. Must be a valid UNIX timestamp (long).")
    if type(timestamp_last) != int:
        raise HTTPException(status_code=400,
                            detail="Invalid type for timestamp_last. Must be a valid UNIX timestamp (long).")

    # check datatype and convert to list if only passed one datatype

    datatypes = data_request['datatype']
    if type(datatypes) == str:
        datatypes = [datatypes]
    elif type(datatypes) != list:
        raise HTTPException(status_code=400,
                            detail="Invalid type for datatype. Must be list or string expressing value of Enum(RequestableType).")

    # check skip and limit if specified or set it to None
    for param in ['skip', 'limit']:
        if param in data_request:
            if type(data_request[param]) != int or data_request[param] < 0:
                raise HTTPException(status_code=400,
                                    detail="Invalid format for %s parameter. Must be integer number greater or equal than 0." % (
                                        param))
        else:
            data_request[param] = None

    filter_duplicates = False
    if 'filter_duplicates' in data_request:
        if (type(data_request['filter_duplicates']) != bool):
            raise HTTPException(status_code=400,
                                detail="Invalid type for filter_duplicates. Must be boolean (true or false).")
        filter_duplicates = data_request['filter_duplicates']

    recordRequest(user_ids, current_user, request)

    skip = data_request['skip']
    limit = data_request['limit']

    res_data = list()
    num_datasets = 0

    for user_id in user_ids:
        for datatype in datatypes:

            requested_data = dict()
            requested_data['user_id'] = user_id
            requested_data['datatype'] = datatype

            try:
                user_id = ObjectId(user_id)
            except:
                requested_data['data'] = dict()  # user id has invalid format, return empty data set
                res_data.append(requested_data)
                continue

            if datatype == 'UserData':
                requested_data['data'] = extractUserData(user_id, timestamp_first, timestamp_last, skip, limit)
                num_datasets = num_datasets + len(requested_data['data'])
            else:  # sensor data
                requested_data['data'] = extractSensorData(datatype, user_id, timestamp_first, timestamp_last, skip,
                                                           limit, filter_duplicates)
                if 'timestamps' in requested_data['data']:
                    num_datasets = num_datasets + len(requested_data['data']['timestamps'])

            res_data.append(requested_data)

    res = {'count': num_datasets}

    if 'count_only' not in data_request or data_request['count_only'] != True:
        # return data only, if count_only is not set
        res['data'] = res_data

    return res
