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
import {Checkbox, FormControlLabel, FormGroup, TextField} from "@mui/material";
import {CosinussDeviceConfig, GarminDeviceConfig} from "../../models/study.ts";

export const CosinussSensorSection: FC<StudyManagementSectionProps> = ({study, updateStudy}) => {
    const config = study.cosinuss_device_config;

    const onUpdateConfig = (partialConfig: Partial<CosinussDeviceConfig>, unmodified?: boolean) => {
        const newConfig = {...config, ...partialConfig} as CosinussDeviceConfig;
        updateStudy({...study, cosinuss_device_config: newConfig}, true, unmodified);
    }

    return (
        <>
            <FormGroup>
                <FormControlLabel control={
                    <Checkbox
                        checked={config.active}
                        onChange={e => onUpdateConfig({active: e.target.checked})}
                    />
                } label="Use cosinuss° One sensor in this study"/>
            </FormGroup>
            <TextField
                datatype={"time"}
                disabled={!config.active}
                type={"time"}
                label={"Daily Wearing Time Begin"}
                helperText={"Begin of the time period of the day, within the cosinuss° One device should be put on. Format: HH:MM"}
                value={config.wearing_time_begin}
                onChange={
                    e => onUpdateConfig({wearing_time_begin: e.target.value.trim()})}
            />



            <TextField
                datatype={"time"}
                disabled={!config.active}
                type={"time"}
                label={"Daily Wearing Time End"}
                helperText={"End of the time period of the day, within the cosinuss° One device should be put on. Format: HH:MM"}
                value={config.wearing_time_end}
                onChange={
                    e => onUpdateConfig({wearing_time_end: e.target.value.trim()})}
            />


            <TextField
                datatype={"time"}
                disabled={!config.active}
                type={"time"}
                label={"Daily Wearing Time Duration"}
                helperText={"Time duration, for which the cosinuss° One device should be continuously put on. Format: HH:MM"}
                value={config.wearing_time_duration}
                onChange={
                    e => onUpdateConfig({wearing_time_duration: e.target.value.trim()})}
            />


            <TextField
                datatype={"time"}
                disabled={!config.active}
                type={"time"}
                label={"Reminder Time"}
                helperText={"Time of the day when the reminder notification should be displayed, if the device was not put on yet. Format: HH:MM"}
                value={config.reminder_time}
                onChange={
                    e => onUpdateConfig({reminder_time: e.target.value.trim()})}
            />
        </>
    );
};