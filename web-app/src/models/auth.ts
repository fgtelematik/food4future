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


export enum Role {
    Administrator = 'Administrator',
    Nurse = 'Nurse',
    Supervisor = 'Supervisor',
    Participant = 'Participant',
}

export const RoleLabel : {
    // [key in keyof Pick<typeof Role, 'Nurse' |'Participant'>]: string  // typing approach not working
    [key in Role]: string
} = {
    [Role.Nurse]: "Nurse",
    [Role.Participant]: "Participant",
    [Role.Supervisor]: "Scientist",
    [Role.Administrator]: "Manager"
}



export type User = {
    id?: ObjectId
    username?: string
    role: Role
    study_id?: ObjectId
    anamnesis_data?: Record<string, any>
    hsz_identifier?: string
}

export type UserInput = User & {
    new_password?: string
    email?: string
}

export type ResendMailRequest = {
    email: string
    reset_password: boolean
}

export type TestUsernameResult = "OK" | "UserExists" | "InvalidName";
