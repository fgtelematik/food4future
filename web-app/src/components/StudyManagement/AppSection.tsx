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

import {FC} from "react";
import {StudyManagementSectionProps} from "./interface.ts";
import {TextField} from "@mui/material";

export const AppSection: FC<StudyManagementSectionProps> = ({study, updateStudy}) => {

    const validateTimeInput = (value: string) => {
        const re = /^(0[0-9]|1[0-9]|2[0-3]):[0-5][0-9]$/;
        return !value || re.test(value);
    }

    const invalidFormat = !validateTimeInput(study.reminder_time ?? "");

    const helperText = invalidFormat ?
        <p>You must enter a valid Time in the format <b>HH:MM</b> (24h) or leave the field blank to disable the
            reminder.</p> :
        <p>Time of day when the participant gets a reminder to fill out the questionnaire (if not already
            done)<br/>Format: <b>HH:MM</b> (24h) | Leave blank to disable reminder.</p>;

    return (
        <>
            <TextField
                datatype={"time"}
                type={"time"}
                label={"Reminder Time"}
                helperText={helperText}
                error={invalidFormat}
                value={study.reminder_time}
                onChange={
                    e => updateStudy({...study, reminder_time: e.target.value.trim()}, validateTimeInput(e.target.value.trim()))}
            />

            <TextField
                datatype={"number"}
                type={"number"}
                label={"Auto Background Data Sync Interval (minutes)"}
                helperText={"Interval in minutes when the app should automatically sync the data with the server in background, if the user has a WiFi connection." +
                    " Minimum: 30 minutes"}
                inputProps={{min: 30}}
                value={study.server_autosync_interval}
                error={isNaN(study.server_autosync_interval) || study.server_autosync_interval < 30}
                onChange={(e) => {
                    const newInterval = parseInt(e.target.value);
                    updateStudy({...study, server_autosync_interval: newInterval}, newInterval >= 30);
                }}
            />

            <TextField
                datatype={"number"}
                type={"number"}
                label={"Maximum Background Data Sync age (minutes)"}
                helperText={"If the last sync time is older than this value, the app will notify the user, that the data " +
                    "should be synced and allows her/him to trigger a manual sync even in mobile network. " +
                    "Minimum: 60 minutes"}
                inputProps={{min: 60}}
                value={study.server_sync_max_age}
                error={isNaN(study.server_sync_max_age) || study.server_sync_max_age < 60}
                onChange={(e) => {
                    const newInterval = parseInt(e.target.value);
                    updateStudy({...study, server_sync_max_age: newInterval}, newInterval >= 60);
                }}
            />

        </>
    );
};