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

import {LocalizedStr, ObjectId} from "./main.ts";
import {NEW_ELEMENT_ID} from "../hooks/useState/useData.ts";
import dedent from "ts-dedent";


export type GarminDeviceConfig = {
    active: boolean
    health_sdk_license_key: string
    sensor_autosync_interval_minutes: number
    sensor_sync_max_age_minutes: number
    zero_crossings_enabled: boolean
    zero_crossings_interval: number
    steps_enabled: boolean
    stress_enabled: boolean
    stress_interval: number
    heart_rate_enabled: boolean
    heart_rate_interval: number
    bbi_enabled: boolean
    spo2_enabled: boolean
    spo2_interval: number
    respiration_enabled: boolean
    respiration_interval: number
    raw_accelerometer_enabled: boolean
    raw_accelerometer_interval: number
}


export type CosinussDeviceConfig = {
    active: boolean
    wearing_time_begin: string
    wearing_time_end: string
    wearing_time_duration: string
    reminder_time: string
}

export type EmailConfig = {
    participant_subject: LocalizedStr
    participant_body_template: LocalizedStr
    nurse_subject: LocalizedStr
    nurse_body_template: LocalizedStr
    scientist_subject: LocalizedStr
    scientist_body_template: LocalizedStr
}


export type Study = {
    id: ObjectId
    title: LocalizedStr
    default_runtime_days: number
    initial_food_enum?: ObjectId
    static_data_form?: ObjectId
    user_data_form?: ObjectId
    reminder_time?: string
    server_autosync_interval: number
    server_sync_max_age: number
    email_config: EmailConfig
    garmin_device_config: GarminDeviceConfig
    cosinuss_device_config: CosinussDeviceConfig
    modified?: boolean
}

export const makeNewStudy = (title: string): Study => ({
    id: NEW_ELEMENT_ID,
    title,
    default_runtime_days: 21,
    garmin_device_config: {
        active: false,
        health_sdk_license_key: "",
        sensor_autosync_interval_minutes: 60,
        sensor_sync_max_age_minutes: 120,
        bbi_enabled: true,
        zero_crossings_enabled: true,
        zero_crossings_interval: 30,
        steps_enabled: true,
        stress_enabled: true,
        stress_interval: 10,
        heart_rate_enabled: true,
        heart_rate_interval: 30,
        spo2_enabled: false,
        spo2_interval: 30,
        respiration_enabled: false,
        respiration_interval: 10,
        raw_accelerometer_enabled: false,
        raw_accelerometer_interval: 30
    },
    cosinuss_device_config: {
        active: false,
        wearing_time_begin: "19:00",
        wearing_time_end: "22:00",
        wearing_time_duration: "00:45",
        reminder_time: "19:30",
    },
    email_config: {
        participant_subject: `Your participation in the ${title} study`,
        participant_body_template: dedent`
        Dear participant,

        thank you for participating in ${title}! 
        
        With this e-mail you will receive your personal access to study participation via your food4future app. 
        
        Scan the enclosed QR code with the camera of your smartphone to download the f4f StudyCompanion app. Alternatively, you can also call up the following link on your smartphone:
        %apk_url%
        
        Important: For the installation of the downloaded app to work, you may need to give your browser (e.g. Chrome) permission to install apps when asked.
        
        Please open the app and tap on "Connect to f4f service", then on the QR code symbol and then scan the attached QR code again.
        
        If this does not work, you can also log in to the app in the same way using the following access data:
        
            Username: %username%
            Password: %password%%nopassword:(specified by you during recording)%
        
        %nopassword:If the login via QR code does not work and you no longer know the password assigned during registration, please contact us.%
        
        If you have any further questions, please contact your study service center.
        
        Thank you very much.
        Your ${title} team.
        `,
        nurse_subject: `You were added as nurse for the ${title} study`,
        nurse_body_template: dedent`
        Hello,
        
        you were added as nurse for the ${title} study.
        
        With this e-mail you will receive your personal access to the food4future app. 
        You can use the app to add new participants to the study and to add participant information. 
        
        Scan the enclosed QR code with the camera of your smartphone to download the f4f StudyCompanion app. Alternatively, you can also call up the following link on your smartphone:
        %apk_url%
        
        Please open the app and tap on "Connect to f4f service", then on the QR code symbol and then scan the attached QR code again.
        
        If this does not work, you can also log in to the app in the same way using the following access data:
        
            Username: %username%
            Password: %password%%nopassword:(specified by your study administrator)%
        
        %nopassword:If the login via QR code does not work and you no longer know the password assigned during registration, please contact us.%
        
        If you have any further questions, please contact your study service center.
        
        Thank you very much.
        Your ${title} team.
        
        `,
        scientist_subject: `You were added as scientist for the ${title} study`,
        scientist_body_template: dedent`
        Hello,
        
        you were added as scientist for the ${title} study. This allows you to access the data collected from of the participants in this study.
        
        Please use the following link to access the data:
        %backend_url%
        
        Please use the following credentials to log in:
            
                Username: %username%
                Password: %password%%nopassword:(specified by your study administrator)%
                
        If you have any further questions, please contact your study administrator.
        
        Thank you very much.
        Your ${title} team.
        `,
    },
    modified: true,
    reminder_time: "21:00",
    server_autosync_interval: 120,
    server_sync_max_age: 240,
});