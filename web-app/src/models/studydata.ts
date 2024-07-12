/*
 * f4f Study Management Portal (Web App)
 *
 * Copyright (c) 2024 Technical University of Applied Sciences Wildau
 * Author: Philipp Wagner, Research Group Telematics
 * Contact: fgtelematik@th-wildau.de
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation version 2.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import {ObjectId} from "./main.ts";

export enum DataType {
    UserData = 'UserData',
    LabData = 'LabData',
    StaticData = 'StaticData',
    SensorData = 'SensorData',
}

export type StudyDataStats = {
    // Provided by server:
    data_type: DataType
    timestamp_first?: number
    timestamp_last?: number
    num_datasets: number
    sensor_data_type?: string
}

export type StudyDataStatsPerUser = {
    [user_id: ObjectId]: StudyDataStats[]
}

export type StudyDataRequest = {
    user_id:  ObjectId[]
    data_type:  DataType[]
    sensor_data_type: string[]
    timestamp_first?: number
    timestamp_last?: number
    skip?: number
    limit?: number
}

export type ClientSideStats = {
    dataCount: number
    firstTimestamp?: number
    lastTimestamp?: number
}
