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

from bson import ObjectId
from bson.codec_options import CodecOptions
from fastapi import Depends, HTTPException, Request, APIRouter

from routers import session
from enums import Role, DataType
from models import convertJSONToMongo, convertMongoToJSON
from models import db, User

router = APIRouter(
    prefix="/sync",
    tags=["Internal - Data Synchronization (used by Android App)"],
)


def check_and_convert_sync_id(sync_id: str, current_user: User):
    if not ObjectId.is_valid(sync_id):
        raise HTTPException(status_code=404, detail="Invalid sync ID.")

    sync_id = ObjectId(sync_id)

    sync_process = db.syncs.find_one({'_id': sync_id})

    if sync_process == None:
        raise HTTPException(status_code=404, detail="Invalid sync ID.")

    if 'finish_time' in sync_process and sync_process['finish_time'] != None:
        raise HTTPException(status_code=409, detail="This synchronization process was already finished.")

    if sync_process['user_id'] != current_user.id:
        raise HTTPException(status_code=403,
                            detail="Current user is not authorized to access the specified synchronization process.")

    return sync_id


def delete_incomplete_sync_process_data(current_user):
    unfinished_syncs_filter = {'user_id': current_user.id, 'finish_time': None}

    unfinished_syncs = db.syncs.find(unfinished_syncs_filter)

    for sync in unfinished_syncs:
        # delete all stored data from last sync process, which had not successfully completed
        db.sensor_data.delete_many({'last_sync_id': sync['_id']})
        db.user_data.delete_many({'last_sync_id': sync['_id']})

        # mark all request data from last sync as unsynched
        db.lab_data.update_many({'last_sync_id': sync['_id']}, {'$unset': {'last_sync_id': 1}})

    db.syncs.delete_many(unfinished_syncs_filter)


@router.get("/", include_in_schema=False)
@router.get("")
def create_new_sync_process(current_user: User = Depends(session.currentUser)):
    if current_user.role != Role.Participant:
        raise HTTPException(status_code=403, detail="Only participants can request a synchronization ID.")

        # Delete all data which were not completely synced on last synchronization process
    delete_incomplete_sync_process_data(current_user)

    res = db.syncs.insert_one({
        "start_time": datetime.datetime.utcnow(),
        "user_id": current_user.id
    })

    sync_id = str(res.inserted_id)

    return {"sync_id": sync_id}


@router.get("/{sync_id}/finish")
def finish_sync_process(sync_id: str, current_user: User = Depends(session.currentUser)):
    sync_id = check_and_convert_sync_id(sync_id, current_user)

    sync_process_mod = {"finish_time": datetime.datetime.utcnow()}
    db.syncs.update_one({'_id': sync_id}, {"$set": sync_process_mod})

    # Change download-tagged sync ids to regular sync ids
    filter = {"last_sync_id_download": sync_id}
    mod = {"$rename": {"last_sync_id_download": "last_sync_id"}}

    db.user_data.update_many(filter, mod)
    db.lab_data.update_many(filter, mod)

    return {'success': True}


@router.post("/{sync_id}")
async def put_data(sync_id: str, request: Request, current_user: User = Depends(session.currentUser)):
    if current_user.role != Role.Participant:
        raise HTTPException(status_code=403, detail="Only participants can upload app-measured data.")

    sync_id = check_and_convert_sync_id(sync_id, current_user)
    sync_filter = {'_id': sync_id}
    sync_process = db.syncs.find_one(sync_filter)

    body = await request.json()

    if 'data' not in body or not type(body['data']) is list:
        raise HTTPException(status_code=400, detail="No data specified or data format invalid.")

    data = body['data']

    if 'datatype' not in body:
        raise HTTPException(status_code=400, detail="Missing datatype.")

    datatype = body['datatype']
    if datatype == DataType.UserData.name:
        database = db.user_data
    elif datatype == DataType.SensorData.name:
        database = db.sensor_data
    else:
        # Using this endpoint, LabData cannot be uploaded (only download)! 
        raise HTTPException(status_code=400, detail="Invalid datatype for data upload.")

        # Creating MongoDB index on key "user_id", which heavily improves performance when requesting data through dataprovider
    database.create_index("user_id")  # ignored command if index already exists

    # Creating MongoDB index on key "last_sync_id", otherwise the app request can timeout during create_new_sync_process()
    # since delete_incomplete_sync_process_data() takes too long to find the related sync items
    database.create_index("last_sync_id")  # ignored command if index already exists

    num_deleted = sync_process['num_deleted_' + datatype] if 'num_deleted_' + datatype in sync_process else 0
    num_added = sync_process['num_added_' + datatype] if 'num_added_' + datatype in sync_process else 0
    num_modified = sync_process['num_modified_' + datatype] if 'num_modified_' + datatype in sync_process else 0

    identifiers = []

    for dataset in data:

        # add sync id to new dataset:
        dataset['last_sync_id'] = sync_id
        dataset['user_id'] = current_user.id

        if 'id' in dataset:
            idstr = dataset['id']
            datasetFilter = {'id': idstr}
            convertJSONToMongo(datasetFilter)

            if len(dataset) == 2:
                # 'id' and 'last_sync_id' are the only keys, so DELETE this dataset
                database.delete_one(datasetFilter)  # ignore errors here (multiple deletion) for now
                num_deleted = num_deleted + 1
                identifiers.append(None)
            else:
                # UPDATE existing dataset
                id = dataset['id']
                del dataset['id']
                convertJSONToMongo(dataset)
                res = database.update_one(datasetFilter, {"$set": dataset})
                if res.matched_count == 0:
                    # No field with specified ID was found. 
                    # This state can occur after an interrupted synchronization process when the unsynched data was deleted from server

                    # So we insert new data instead, but track it as modified.

                    newDataset = dict.copy(dataset)
                    newDataset['id'] = id
                    convertJSONToMongo(newDataset)
                    database.insert_one(newDataset)

                num_modified = num_modified + res.modified_count
                identifiers.append(idstr)

        else:
            # no 'id' field found, so CREATE new dataset
            convertJSONToMongo(dataset)
            res = database.insert_one(dataset)
            identifiers.append(str(res.inserted_id))
            num_added = num_added + 1

    db.syncs.update_one(sync_filter, {'$set': {
        'num_added_' + datatype: num_added,
        'num_deleted_' + datatype: num_deleted,
        'num_modified_' + datatype: num_modified
    }})

    return {'identifiers': identifiers}


@router.get("/{sync_id}")
async def get_data(sync_id: str, request: Request, current_user: User = Depends(session.currentUser)):
    if current_user.role != Role.Participant:
        raise HTTPException(status_code=403, detail="Only participants can currently request their data sets.")

    sync_id = check_and_convert_sync_id(sync_id, current_user)
    headers = request.headers

    sendall = 'all' in headers and headers[
        'all'] == 'true'  # if True, send ALL data available on server, not only data that was not yet synced

    if 'datatype' not in headers:
        raise HTTPException(status_code=400, detail="Missing datatype.")

    codec_options = CodecOptions(
        tz_aware=True)  # keep timezone information in result data (always UTC, see https://pymongo.readthedocs.io/en/stable/examples/datetimes.html!)

    datatype = headers['datatype']
    if datatype == DataType.UserData.name:
        database = db.get_collection('user_data', codec_options=codec_options)
    elif datatype == DataType.LabData.name:  # ToDo: Remove Support for LabData
        database = db.get_collection('lab_data', codec_options=codec_options)
    else:
        # Using this endpoint, SensorData cannot be downloaded from app (only uploaded)! 
        raise HTTPException(status_code=400, detail="Invalid datatype for data request.")

    query_filter = {'user_id': current_user.id}

    if sendall == False:
        # if not requesting all data, only send data sets, which have no last_sync_id's attached
        query_filter['last_sync_id'] = None
    else:
        # if requesting "all" data, send all except for these already sent in earlier request during this sync process
        query_filter['last_sync_id'] = {'$ne': sync_id}

    data = database.find(query_filter)
    if 'limit' in headers and headers['limit'].isnumeric():
        data.limit(int(headers['limit']))

    resp = []

    for dataset in data:

        del dataset['user_id']
        if 'last_sync_id' in dataset:
            del dataset['last_sync_id']
        if 'last_sync_id_download' in dataset:
            del dataset['last_sync_id_download']

        tmp_filter = {'_id': dataset['_id']}

        # append this dataset to request
        convertMongoToJSON(dataset)
        resp.append(dataset)

        # mark this dataset as synced within the current sync process 
        # (special mark for download, for that data won't be deleted on incomplet sync process)
        database.update_one(tmp_filter, {'$set': {'last_sync_id_download': sync_id}})

    return {'data': resp}
