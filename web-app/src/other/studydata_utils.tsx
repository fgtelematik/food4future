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

import {ClientSideStats, DataType, StudyDataRequest, StudyDataStatsPerUser} from "../models/studydata.ts";

function filterStats(stats: StudyDataStatsPerUser, request: StudyDataRequest) : StudyDataStatsPerUser {
    const res:StudyDataStatsPerUser = {};
    for(const userId of request.user_id) {
        if(stats[userId])
            res[userId] = stats[userId];
    }

    for (const userId in res ) {
        const userStats = res[userId];
        res[userId] = userStats.filter(stat => {
            if (!request.data_type.includes(stat.data_type))
                return false;

            if (stat.data_type == DataType.SensorData) {
                if (!stat.sensor_data_type || !request.sensor_data_type.includes(stat.sensor_data_type))
                    return false;
            }

            return true;
        });
    }

    return res;
}


/**
 * Generates a client side stats object from the server side stats object.
 * The stats for available data sets, the first and last timestamp are calculated.
 * If a request is given, the stats are filtered according to the request.
 * @param stats
 * @param request
 */
export function generateClientSideStats(stats: StudyDataStatsPerUser, request?: StudyDataRequest) : Partial<ClientSideStats> {
    if(request)
        stats = filterStats(stats, request);

    const dataCount = Object.values(stats).reduce((acc, userStats) => {
        const userCount = userStats.reduce((user_acc, user_val) => {
            return user_acc + user_val.num_datasets
        }, 0);
        return acc + userCount;
    }, 0);

    const firstTimestamps :number[]= [];
    const lastTimestamps :number[]= [];

    for(const userId in stats) {
        const userStats = stats[userId];
        for(const stat of userStats) {
            if(stat.timestamp_first)
                firstTimestamps.push(stat.timestamp_first);
            if(stat.timestamp_last)
                lastTimestamps.push(stat.timestamp_last);
        }
    }

    return {
        dataCount,
        firstTimestamp: firstTimestamps.length > 0 ? Math.min(...firstTimestamps) : undefined,
        lastTimestamp: lastTimestamps.length > 0 ? Math.max(...lastTimestamps) : undefined,
    }
}