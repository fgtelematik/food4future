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

import {FC, useEffect, useState} from "react";
import useApi from "../hooks/useApi";
import {useDataState} from "../hooks/useState/useData.ts";
import {Button, CircularProgress} from "@mui/material";
import {DataType, StudyDataRequest, StudyDataStatsPerUser} from "../models/studydata.ts";
import "../style/StudyDataOverview.css"
import {ObjectId} from "../models/main.ts";
import {generateClientSideStats} from "../other/studydata_utils.tsx";
import {toTimeStr} from "../utils.ts";

type ColumnType = {
    dataType: DataType;
    sensorDataType?: string;
}

const dataTypeLabels: { [key in DataType]: string } = {
    [DataType.SensorData]: "Sensor Data",
    [DataType.UserData]: "Daily User Input",
    [DataType.LabData]: "Lab Data (deprecated!)",
    [DataType.StaticData]: "Profile Data",
}

const initialRequest: StudyDataRequest = {
    data_type: [],
    sensor_data_type: [],
    user_id: [],
}


function getAvailableDataColumnTypes(stats: StudyDataStatsPerUser): ColumnType[] {
    const res: ColumnType[] = [];

    for (const userId in stats) {
        for (const userStats of stats[userId]) {
            const dataType = userStats.data_type;
            const isSensorData = dataType == DataType.SensorData;

            const isExistingColumnType = isSensorData ?
                res.some(columnType =>
                    columnType.dataType === DataType.SensorData &&
                    columnType.sensorDataType === userStats.sensor_data_type
                )
                :
                res.some(columnType =>
                    columnType.dataType === dataType
                )


            if (!isExistingColumnType) {
                const newColumnType: ColumnType = {dataType}
                if (isSensorData)
                    newColumnType.sensorDataType = userStats.sensor_data_type;
                res.push(newColumnType);
            }
        }
    }

    return res.reverse(); // reverse to have SensorData columns at the end
}


export const StudyDataOverview: FC = () => {


    const stats = useDataState(state => state.dataStats);
    const users = useDataState(state => state.users);

    const [request, setRequest] = useState<StudyDataRequest>(initialRequest);
    const [highlightedUser, setHighlightedUser] = useState<ObjectId | null | "all">(null);
    const [highlightedColumnType, setHighlightedColumnType] = useState<ColumnType | null>(null);
    const [isGeneratingCSV, setIsGeneratingCSV] = useState(false);

    useEffect(() => {
        const doFetch = async () => {
            await useApi().fetchUsers();
            await useApi().fetchStudyDataStats();

        }

        doFetch();

    }, []);

    const columns = !!stats ? getAvailableDataColumnTypes(stats) : [];

    useEffect(() => {
        if(!columns || !stats)
            return;

        // Pre-select all: (has proved to be unfavorable)
        // setRequest({
        //     user_id: Object.keys(stats),
        //     data_type: columns
        //         .map(c=>c.dataType)
        //         .filter( // Exclude duplicates
        //             (dt, index, self) => index === self.indexOf(dt)
        //         ),
        //     sensor_data_type: columns.map(c=>c.sensorDataType!).filter(sdt => !!sdt),
        // })

        // Pre-select none:
        setRequest(initialRequest);

    }, [stats]);


    if (!stats || !users) {
        return (
            <div>
                <h1>Loading Study Data Information...</h1>
                <p><CircularProgress/></p>
            </div>
        )
    }

    const getUserLabel = (userId: ObjectId) => {
        const user = users.find(user => user.id === userId);

        if (user?.hsz_identifier)
            return user.hsz_identifier;

        if (user?.anamnesis_data) {
            // try to extract an identifier (field labeled "identifier" or "id") from the static data
            let idField = Object.keys(user.anamnesis_data).find(key => key.toLowerCase().includes("identifier"));
            if (!idField)
                idField = Object.keys(user.anamnesis_data).find(key => key.toLowerCase().includes("id"));
            if (idField) {
                const id = user.anamnesis_data[idField];
                if (id && typeof id === "string")
                    return id;
            }
        }

        if (user?.username)
            return user.username;

        return userId;
    }


    const generateCSV = async () => {
        setIsGeneratingCSV(true);
        try {
            await useApi().generateStudyDataCSV(request);
        } catch (e) {
            console.error(e);
        } finally {
            setIsGeneratingCSV(false);
        }
    }

    const toggleRequestUser = (userId: ObjectId) => {
        const newRequest = {...request};
        const idx = newRequest.user_id.indexOf(userId);

        if (idx >= 0)
            newRequest.user_id.splice(idx, 1);
        else
            newRequest.user_id.push(userId);

        setRequest(newRequest);
    }

    const toggleRequestAllUsers = () => {
        const newRequest = {...request};
        if (newRequest.user_id.length > 0)
            newRequest.user_id = [];
        else
            newRequest.user_id = users.map(user => user.id!);
        setRequest(newRequest);
    }

    const toggleRequestDataType = (dataType: ColumnType) => {
        const newRequest = {...request};

        let doRemove = newRequest.data_type.includes(dataType.dataType);

        if (dataType.dataType == DataType.SensorData) {
            const idx = newRequest.sensor_data_type.indexOf(dataType.sensorDataType!);
            if (idx >= 0)
                newRequest.sensor_data_type.splice(idx, 1);
            else
                newRequest.sensor_data_type.push(dataType.sensorDataType!);

            if (newRequest.sensor_data_type.length > 0)
                doRemove = false;
        }

        if (doRemove) {
            const idx = newRequest.data_type.indexOf(dataType.dataType);
            if (idx >= 0)
                newRequest.data_type.splice(idx, 1);
        } else if(!newRequest.data_type.includes(dataType.dataType)) {
            newRequest.data_type.push(dataType.dataType);
        }

        setRequest(newRequest);
    }

    const isAllUsersRequested = () => {
        return (request.user_id.length == Object.keys(stats).length)
    }

    const isUserRequested = (userId: ObjectId) => {
        return request.user_id.includes(userId);
    }

    const isDataTypeRequested = (dataType: ColumnType) => {
        if (dataType.dataType == DataType.SensorData)
            return request.sensor_data_type.includes(dataType.sensorDataType!);
        else
            return request.data_type.includes(dataType.dataType);
    }

    const isRequested = (userId: ObjectId, dataType: ColumnType) => {
        return isUserRequested(userId) && isDataTypeRequested(dataType);
    }

    const isDataTypeHighlighted = (dataType: ColumnType) => {
        return !!highlightedColumnType &&
            highlightedColumnType?.dataType === dataType.dataType &&
            highlightedColumnType?.sensorDataType === dataType.sensorDataType;
    }

    const isUserHighlighted = (userId: ObjectId) => {
        return highlightedUser === userId;
    }

    const isHighlighted = (userId: ObjectId, dataType: ColumnType) => {
        return isUserHighlighted(userId) && isDataTypeHighlighted(dataType);
    }


    const getClassNames = (selected: boolean, highlighted: boolean, noData: boolean) => {
        const classes: string[] = [];
        if (highlighted)
            classes.push("highlighted");
        if (selected)
            classes.push("selected");
        if (noData)
            classes.push("no-data");
        return classes.join(" ");
    }

    const totalStats = generateClientSideStats(stats);
    const selectedStats = generateClientSideStats(stats, request);
    const numParticipantsWithoutData = users.filter(user => !stats[user.id!]).length;


    return (
        <div className={"study-data"}>
            <h1>Study Data</h1>

            <h2>Overview</h2>
            <table className={"study-data-stats"}>
                <thead>
                <tr>
                    <th></th>
                    <th>Total</th>
                    <th>Selection</th>
                </tr>
                </thead>

                <tbody>
                <tr>
                    <th>Number of Participants:</th>
                    <td>{Object.keys(stats).length}</td>
                    <td>{request.user_id.length}</td>
                </tr>
                <tr>
                    <th>Number of Participants without data:</th>
                    <td>{numParticipantsWithoutData}</td>
                    <td></td>
                </tr>

                <tr>
                    <th>Number of Data Sets:</th>
                    <td>{totalStats.dataCount}</td>
                    <td>{selectedStats.dataCount}</td>
                </tr>
                <tr>
                    <th>Oldest Data Set:</th>
                    <td>{toTimeStr(totalStats.firstTimestamp)}</td>
                    <td>{toTimeStr(selectedStats.firstTimestamp) }</td>
                </tr>
                <tr>
                    <th>Newest Data Set:</th>
                    <td>{toTimeStr(totalStats.lastTimestamp)}</td>
                    <td>{toTimeStr(selectedStats.lastTimestamp)}</td>
                </tr>

                </tbody>
            </table>
            <h2>Download Data</h2>
            <p>The following table shows the number of data sets for each participant and data type.</p>
            <p><b>Please click the Sensor Types or Participant rows to (de-)select data parts for download!</b></p>
            <table className={"study-data-matrix"}>
                <thead>
                <tr>
                    <th
                        className={getClassNames(isAllUsersRequested(), highlightedUser == "all", false)}
                        onMouseOver={() => setHighlightedUser("all")}
                        onMouseOut={() => setHighlightedUser(null)}
                        onClick={() => toggleRequestAllUsers()}
                    >
                        [Participant]
                    </th>
                    {
                        columns.map((column, idx) => {
                            const title = column.dataType == DataType.SensorData && column.sensorDataType ?
                                column.sensorDataType : dataTypeLabels[column.dataType];
                            return (
                                <th
                                    key={idx}
                                    className={getClassNames(isDataTypeRequested(column), isDataTypeHighlighted(column), false)}
                                    onMouseOver={() => setHighlightedColumnType(column)}
                                    onMouseOut={() => setHighlightedColumnType(null)}
                                    onClick={() => toggleRequestDataType(column)}
                                >
                                    {title}
                                </th>
                            )
                        })
                    }
                </tr>
                </thead>
                <tbody>
                {
                    Object.keys(stats).map(userId => {
                        const userStats = stats[userId];
                        return (
                            <tr key={userId}>
                                <td key={"user"}
                                    className={getClassNames(isUserRequested(userId), isUserHighlighted(userId) || highlightedUser == "all", false)}
                                    onMouseOver={() => setHighlightedUser(userId)}
                                    onMouseOut={() => setHighlightedUser(null)}
                                    onClick={() => toggleRequestUser(userId)}
                                >{getUserLabel(userId)}
                                </td>
                                {
                                    columns.map((column, idx) => {
                                        const userStatsForColumn = userStats.find(userStats => {
                                            const isSensorData = column.dataType == DataType.SensorData;
                                            return userStats.data_type == column.dataType &&
                                                (!isSensorData || userStats.sensor_data_type == column.sensorDataType)
                                        });

                                        const requested = isRequested(userId, column);
                                        const highlighted = isHighlighted(userId, column);
                                        const noData = !userStatsForColumn;


                                        return (
                                            <td className={"data-cell " + getClassNames(requested, highlighted, noData)}
                                                key={idx}
                                            >
                                                {
                                                    userStatsForColumn ? userStatsForColumn.num_datasets : "n/a"
                                                }
                                            </td>
                                        )
                                    })
                                }
                            </tr>
                        )
                    })
                }
                </tbody>
            </table>

            <div className={"action-buttons"}>
                <Button
                    onClick={generateCSV}
                    variant={"contained"}
                    disabled={selectedStats.dataCount == 0 || isGeneratingCSV}
                >{isGeneratingCSV ? <CircularProgress/> : "Generate and Download CSV with " + selectedStats.dataCount + " data sets"}</Button>
            </div>
            <p>Please note, <i>food consumption</i> inputs and <i>daily questions</i> inputs are currently combined in the <i>Daily User Input</i> data type and cannot be extracted separately.<br/>This might be separated in future versions of Study Management Portal.</p>
            <h2>RESTful API</h2>
            <p>You can also access all information and specific data through our RESTful endpoints.</p>
            <p><b><a target={"_blank"} href={"/docs#/Data%20Request%20(v2)"}>Click this link</a></b> to open the OpenAPI specification (Swagger UI).
            Use the entpoints categorized under <b>Data Request (v2)</b> for data and stats access.</p>
            <p>You will first need to acquire a Bearer Token at the <b>/token</b> endpoint using your Scientist credentials
                to be able to access the Data Request endpoints.</p>

        </div>
    )
}