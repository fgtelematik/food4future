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

import {FC, useEffect} from "react";
import {StudyManagementSectionProps} from "./interface.ts";
import {Checkbox, Divider, FormControlLabel, FormGroup, TextField} from "@mui/material";
import {EmailConfig, GarminDeviceConfig} from "../../models/study.ts";

type LoggingDataSetting = {
    label: string,
    key: string,
    min: number,
    default: number,
    max: number
}

const loggingDataSettings: LoggingDataSetting[] = [
    {
        label: "Energy / Zero Crossings",
        key: "zero_crossings",
        min: 30,
        default: 30,
        max: 3600
    },
    {
        label: "Beat-to-Beat-Interval",
        key: "bbi",
        default: 0,
        min: 0,
        max: 0,
    },
    {
        label: "Step Count",
        key: "steps",
        default: 60,
        min: 60,
        max: 60,
    },
    {
        label: "Stress Score",
        key: "stress",
        default: 10,
        min: 1,
        max: 3600,
    },
    {
        label: "Heart Rate",
        key: "heart_rate",
        default: 30,
        min: 1,
        max: 3600,
    },
    {
        label: "SPO2",
        key: "spo2",
        default: 30,
        min: 1,
        max: 3600,
    },
    {
        label: "Respiration Rate",
        key: "respiration",
        default: 10,
        min: 1,
        max: 3600
    },
    {
        label: "Accelerometer (raw)",
        key: "raw_accelerometer",
        default: 30,
        min: 1,
        max: 3600,
    }
];

export const GarminSensorSection: FC<StudyManagementSectionProps> = ({study, updateStudy}) => {

    const config = study.garmin_device_config;
    const active = config.active;

    const hasAnyInvalidInterval = (newConfig: GarminDeviceConfig) => loggingDataSettings.some(setting => {
            const interval_key = setting.key + "_interval" as keyof GarminDeviceConfig;
            const interval = newConfig[interval_key] as number;
            if (setting.min == setting.max)
                return false;
            return isNaN(interval) || interval < setting.min || interval > setting.max;
        }
    );

    const isValid = (newConfig: GarminDeviceConfig) => !newConfig.active ||
        newConfig.active &&
        !!newConfig.health_sdk_license_key &&
        !isNaN(newConfig.sensor_autosync_interval_minutes) &&
        newConfig.sensor_autosync_interval_minutes >= 30 &&
        !isNaN(newConfig.sensor_sync_max_age_minutes) &&
        newConfig.sensor_sync_max_age_minutes >= 60 &&
        !hasAnyInvalidInterval(newConfig)
    ;

    const onUpdateConfig = (partialConfig: Partial<GarminDeviceConfig>, unmodified?: boolean) => {
        const newConfig = {...config, ...partialConfig} as GarminDeviceConfig;
        const valid = isValid(newConfig);
        updateStudy({...study, garmin_device_config: newConfig}, valid, unmodified);
    }


    useEffect(() => {
        onUpdateConfig({}, true);
    }, []);

    const renderLoggingDataSetting = (setting: LoggingDataSetting) => {
            const interval_key = setting.key + "_interval" as keyof GarminDeviceConfig;
            const enabled_key = setting.key + "_enabled" as keyof GarminDeviceConfig;
            const isIntervalConfigurable = setting.min !== setting.max;
            const interval = isIntervalConfigurable ? config[interval_key] as number : setting.default;
            const isInvalidInterval = isNaN(interval) || interval < setting.min || interval > setting.max;

            const onEnable = (enabled: boolean) => {
                if (isInvalidInterval) {
                    onUpdateConfig({[enabled_key]: enabled, [interval_key]: setting.default});
                } else {
                    onUpdateConfig({[enabled_key]: enabled});
                }
            }

            return (
                <tr key={study.id + "-" + setting.label}>
                    <td>{setting.label}</td>
                    <td><Checkbox
                        disabled={!config.active}
                        checked={!!config[enabled_key] as boolean}
                        onChange={e => onEnable(e.target.checked)}
                    /></td>
                    <td>{setting.min}</td>
                    <td>
                        <TextField
                            datatype={"number"}
                            type={"number"}
                            size={"small"}
                            style={{width: "5em"}}
                            placeholder={setting.default.toString()}
                            disabled={!active || !config[enabled_key] || !isIntervalConfigurable}
                            error={isNaN(interval) || interval < setting.min || interval > setting.max}
                            value={interval}
                            inputProps={{min: setting.min, max: setting.max}}
                            onFocus={e => e.target.select()}
                            onChange={(e) => {
                                if (!isIntervalConfigurable)
                                    return;
                                const newInterval = parseInt(e.target.value);
                                onUpdateConfig({[interval_key]: newInterval});
                            }}
                        />
                    </td>
                    <td>{setting.max}</td>
                    <td>{setting.default}</td>
                </tr>
            );
        }
    ;


    return (
        <>
            <FormGroup>
                <FormControlLabel control={
                    <Checkbox
                        checked={config.active}
                        onChange={e => onUpdateConfig({active: e.target.checked})}
                    />
                } label="Use Garmin Sensor Device in this study"/>
            </FormGroup>
            <TextField
                label={"Garmin Health SDK License Key"}
                disabled={!config.active}
                value={config.health_sdk_license_key ?? ""}
                error={active && !config.health_sdk_license_key}
                helperText={<>Please enter the license key for the Garmin Health Standard SDK to use Garmin Wearables in
                    this study.<br/>You can purchase a license key at <a target={"_blank"}
                                                                         href="https://developer.garmin.com/health-sdk/overview/">Garmin
                        Health SDK</a>.</>}
                onChange={(e) => {
                    onUpdateConfig({health_sdk_license_key: e.target.value});
                }}
            />
            <TextField
                datatype={"number"}
                type={"number"}
                disabled={!active}
                label={"Auto Background Sync Interval (minutes)"}
                helperText={"Interval in minutes the app will auto-request downloading logging data from the Garmin device. Minimum: 30 minutes"}
                inputProps={{min: 30}}
                value={config.sensor_autosync_interval_minutes}
                error={isNaN(config.sensor_autosync_interval_minutes) || config.sensor_autosync_interval_minutes < 30}
                onChange={(e) => {
                    const newInterval = parseInt(e.target.value);
                    onUpdateConfig({sensor_autosync_interval_minutes: newInterval});
                }}
            />
            <TextField
                datatype={"number"}
                type={"number"}
                disabled={!active}
                label={"Sync Data Maximum Age (minutes)"}
                helperText={"Maximum age in minutes of the sensor data, before the user will be actively asked to trigger synchronization with the Garmin device. Minimum: 60 minutes"}
                inputProps={{min: 60}}
                value={config.sensor_sync_max_age_minutes}
                error={isNaN(config.sensor_sync_max_age_minutes) || config.sensor_sync_max_age_minutes < 60}
                onChange={(e) => {
                    const newInterval = parseInt(e.target.value);
                    onUpdateConfig({sensor_sync_max_age_minutes: newInterval});
                }}
            />
            <Divider/>
            <h2>Garmin Logging Settings</h2>
            <p>Please specifiy, which types of logging data you want the wearable to collect and in what intervals (in
                seconds) they should be measured.</p>
            <table>
                <thead>
                <tr>
                    <th>Data Type</th>
                    <th>Enabled</th>
                    <th>Minimum</th>
                    <th>Interval</th>
                    <th>Maximum</th>
                    <th>Default</th>
                </tr>
                </thead>
                <tbody>
                {
                    loggingDataSettings.map(renderLoggingDataSetting)
                }
                </tbody>
            </table>
        </>
    );
};