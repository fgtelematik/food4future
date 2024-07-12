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

from models import PyObjectId, db
from utils import debug_print


def _extend_lists(data: dict, data_to_extend: dict):
    for key in data_to_extend:
        # check data conformity
        if type(data_to_extend[key]) != list or key not in data or type(data[key]) != list:
            continue

        data[key].extend(data_to_extend[key])


def _truncate_lists(data: dict, n_head_elements: int | None, n_tail_elements: int | None):
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


def _filter_sensor_data(sensor_data: dict,
                        timestamp_first: int | None,
                        timestamp_last: int | None,
                        extracted_timestamps: set):
    # disambiguate time range
    if not timestamp_first:
        timestamp_first = 0

    if not timestamp_last:
        timestamp_last = 0

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

        in_time_range = timestamp_first <= t
        if timestamp_last > 0:
            in_time_range = in_time_range and t <= timestamp_last

        if in_time_range and not duplicate:
            # timestamp is in range.
            # Copy the corresponding effective data of all lists into resulting dataset
            for key in res:
                res[key].append(effective_sensor_data[key][i])

    return res


def extract_sensor_data(datatype: str,
                        user_id: PyObjectId,
                        timestamp_first: int | None,
                        timestamp_last: int | None,
                        skip: int | None,
                        limit: int | None,
                        filter_duplicates: bool = False):
    # generate request
    req = {'type': datatype, 'user_id': user_id}
    if timestamp_first or timestamp_last:
        req["timestamps"] = {}
        if timestamp_first:
            req["timestamps"]["$gte"] = timestamp_first
        if timestamp_last:
            req["timestamps"]["$lte"] = timestamp_first

    # this request might find multiple sensor data sets, which most likely contain data, which is also outside
    # the specified time range
    debug_print("extract_sensor_data: Executing DB request: " + str(req))
    cur = db.sensor_data.find(req)
    debug_print("extract_sensor_data: DB request completed.")

    res = None
    timestamps = None

    num_skipped = 0
    count = 0

    for dataset in cur:
        filtered_dataset = _filter_sensor_data(dataset, timestamp_first, timestamp_last, timestamps)
        num_elements = len(filtered_dataset['timestamps'])

        if num_elements == 0:
            continue

        # skip heading datasets or elements of current dataset
        if skip and skip > 0:
            skip_diff = skip - num_elements - num_skipped
            if skip_diff >= 0:
                # whole current dataset is skipped
                num_skipped = num_skipped + num_elements
                continue
            else:
                # some heading elements of the current dataset might be truncated due to skip

                # numbers of heading elements of current data set to truncate:
                num_trunc_left = num_elements + skip_diff
                # (might be 0 if num_dataset is divisor of skip, but truncateLists does nothing in this case)

                _truncate_lists(filtered_dataset, num_trunc_left, None)
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
                _truncate_lists(filtered_dataset, None, -limitdiff)
                num_elements = num_elements + limitdiff

        if res is None:
            res = filtered_dataset

            if filter_duplicates:
                timestamps = set(filtered_dataset['timestamps'])
        else:
            _extend_lists(res, filtered_dataset)

            if filter_duplicates:
                timestamps = timestamps.union(set(filtered_dataset['timestamps']))

        count = count + num_elements

    if res is None:
        res = dict()  # return empty dict, if no matched data was found

    return res


def extract_user_data(user_id: PyObjectId,
                      timestamp_first: int | None,
                      timestamp_last: int | None,
                      skip: int | None,
                      limit: int | None):
    time_start = None
    time_end = None

    # since we only have user data concerning whole days,
    # we make timestamp_first be midnight of its referred day (00:00) in seconds
    if timestamp_first:
        timestamp_first = int(timestamp_first / 86400000) * 86400
        time_start = datetime.fromtimestamp(timestamp_first)
    # and make timestamp_last be the midnight of the day following its referred day in seconds
    if timestamp_last:
        timestamp_last = int(timestamp_last / 86400000) * 86400 + 86400
        time_end = datetime.fromtimestamp(timestamp_last)
    # Note: 24 hours have 86400 seconds.

    req = {'user_id': user_id}
    if time_start or time_end:
        req["effective_day"] = {}
        if time_start:
            req["effective_day"]["$gte"] = time_start
        if time_end:
            req["effective_day"]["$lte"] = time_end

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
