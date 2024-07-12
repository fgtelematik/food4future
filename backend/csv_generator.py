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

import csv
import io
from typing import Dict, List

from enums import DataType
from models import StudyDataPerUser, get_user_identifier
from utils import dict_without_keys, dict_only_keys, debug_print

# As I figured out, reducing the amount of 'yield' calls for the StreamResponse improves performance massively,
# so we will buffer the CSV output and only yield when the buffer is full or when the generator is finished.
MIN_STREAM_BUFFER_SIZE = 1024 * 1024 * 1  # 1 MB


def _generator_warning(msg: str):
    # TODO: Find way to properly handle warnings and notify user,
    #  since warnings mean data loss during csv generation resulting in incomplete data sets
    print("WARNING (CSV Generator): " + msg)


def _search_keys(data: StudyDataPerUser) -> List[str]:
    sensor_data_types = []
    keys = []

    sensor_data_type_index = 1

    if len(data.items()) > 1:
        keys.append("user_id") # only add user ID column if more than one user is requested
        sensor_data_type_index += 1

    keys.append("data_type")

    food_keys = []

    any_sensor_data = False

    def add_data_keys(data_dict: Dict, is_foodlist=False):
        if type(data_dict) != dict:
            _generator_warning("WARNING (CSV Generator): Invalid data dict: " + str(data_dict))
            return

        target_list = keys if not is_foodlist else food_keys

        for key in data_dict.keys():
            if key == "foodlist":
                continue

            if is_foodlist:
                key = "food." + key

            if key not in target_list:
                target_list.append(key)

    for user_id, datasets in data.items():
        for dataset in datasets:
            data_type = dataset.data_type
            sensor_data_type = dataset.sensor_data_type
            data_dict = dataset.data

            if data_type == DataType.SensorData and sensor_data_type and type(data_dict) == dict:
                any_sensor_data = True
                if sensor_data_type in sensor_data_types:
                    continue  # only check fields of sensor data once per sensor data type
                    # assuming same sensor data type has same field
                add_data_keys(data_dict)
                sensor_data_types.append(sensor_data_type)

            if data_type == DataType.StaticData and type(data_dict) == dict:
                add_data_keys(data_dict)

            if data_type == DataType.UserData and type(data_dict) == list:
                for user_data in data_dict:
                    add_data_keys(user_data)

                    if "foodlist" in user_data and type(user_data["foodlist"]) == list:
                        for foodlist in user_data["foodlist"]:
                            add_data_keys(foodlist, is_foodlist=True)

    if any_sensor_data: # Add sensor_data_type after data_type column, if available
        keys.insert(sensor_data_type_index, 'sensor_data_type')

    return keys + food_keys


def generate_csv(study_data: StudyDataPerUser):
    debug_print("generate_csv() called.\nSearching for available keys...")

    keys = _search_keys(study_data)
    debug_print("Found keys: " + str(keys))

    out = io.StringIO(newline='')
    writer = csv.DictWriter(out, fieldnames=keys)

    buffer_bytes_written = 0

    def try_flush_buffer(force=False):
        nonlocal buffer_bytes_written

        res = ""

        if (
                (force and buffer_bytes_written > 0)
                or buffer_bytes_written > MIN_STREAM_BUFFER_SIZE
        ):
            res = out.getvalue()
            out.seek(0)
            out.truncate(0)
            buffer_bytes_written = 0

        return res

    def generate_csv_line(line_data: Dict | None, is_header=False):
        nonlocal buffer_bytes_written

        if is_header:
            num_bytes = writer.writeheader()
        else:
            num_bytes = writer.writerow(line_data)
        buffer_bytes_written += num_bytes

        return try_flush_buffer()

    yield generate_csv_line(None, is_header=True)

    more_users = len(study_data.items()) > 1

    for user_id, datasets in study_data.items():
        user_identifier = get_user_identifier(user_id)
        debug_print("Generating CSV lines for user " + user_identifier + "...")
        total = len(datasets)

        for i, dataset in enumerate(datasets):
            data_type = dataset.data_type
            sensor_data_type = dataset.sensor_data_type
            data = dataset.data
            data_dict = {"data_type": data_type}

            if more_users:
                data_dict["user_id"] = user_identifier

            if data_type == DataType.SensorData and sensor_data_type and type(data) == dict:
                debug_print("Processing data set " + str(i + 1) + "/" + str(total) + " (" + sensor_data_type + ")...")

                data_dict["sensor_data_type"] = sensor_data_type

                # Test, if data is a dict consisting of lists of same length and has at least one list,
                # otherwise continue:
                keys = list(data.keys())
                if (len(keys) == 0 or
                        not all(type(data[key]) == list for key in keys) or
                        not all(len(data[key]) == len(data[keys[0]]) for key in keys)):
                    _generator_warning("WARNING (CSV Generator): Invalid sensor data dict: " + str(data))
                    continue

                length = len(data[keys[0]])

                for i in range(length):
                    set_nr = i + 1
                    if set_nr % 10000 == 0:
                        debug_print("Processing data point " + str(set_nr) + "/" + str(length) + " (" + sensor_data_type + ")...")

                    subset = {}
                    for key in keys:
                        subset[key] = data[key][i]

                    buffer_data = generate_csv_line(data_dict | subset)
                    if buffer_data:
                        yield buffer_data

            if data_type == DataType.StaticData and type(data) == dict:
                debug_print("Processing data set " + str(i + 1) + "/" + str(total) + " (StaticData)...")

                buffer_data = generate_csv_line(data_dict | data)
                if buffer_data:
                    yield buffer_data

            if data_type == DataType.UserData and type(data) == list:
                for user_data in data:
                    debug_print("Processing data set " + str(i + 1) + "/" + str(total) + " (UserData)...")

                    # first add questionnaire data without foodlist
                    question_data = dict_without_keys(user_data, ["foodlist"])
                    if dict_without_keys(question_data, ["effective_day", "modification_time", "creation_time"]):
                        buffer_data = generate_csv_line(data_dict | question_data)
                        if buffer_data:
                            yield buffer_data

                    # Now we will add individual lines for each consumption. For each consumption
                    # we need some meta data for temporal reference. Should be defined in those fields:
                    meta_data = dict_only_keys(user_data, ["effective_day", "modification_time", "creation_time"])
                    if not meta_data:
                        # If none of these fields exist, we use the whole question dataset as fallback
                        meta_data = question_data

                    if "foodlist" in user_data and type(user_data["foodlist"]) == list:
                        for foodlist in user_data["foodlist"]:
                            # add prefix food. to all keys of foodlist
                            foodlist = {"food." + key: value for key, value in foodlist.items()}

                            buffer_data = generate_csv_line(data_dict | meta_data | foodlist)
                            if buffer_data:
                                yield buffer_data

    buffer_data = try_flush_buffer(True)
    if buffer_data:
        yield buffer_data

    debug_print("CSV generation finished.")
