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
import {Role} from "./auth.ts";

export enum PermissionType {
    Read = "Read",
    Edit = "Edit"
}

export enum IdentifierCheckResult {
    AlreadyInUse = "AlreadyInUse",
    Invalid = "Invalid",
    Reserved = "Reserved",
    OK = "OK"
}

export const identifierHelperText = {
    [IdentifierCheckResult.OK]: "",
    [IdentifierCheckResult.AlreadyInUse]: "This identifier is already in use.",
    [IdentifierCheckResult.Reserved]: "This identifier is reserved for internal usage.",
    [IdentifierCheckResult.Invalid]: "Please enter a field identifier.",
}


export enum FieldType {
    StringType = "StringType",
    BoolType = "BoolType",
    FloatType = "FloatType",
    IntType  = "IntType",
    EnumType = "EnumType",
    TimeType = "TimeType",
    DateType = "DateType",
    ListType = "ListType",
    FormType = "ADT",
    Container = "Container"
}

export type InputForm = {
    id: ObjectId
    identifier: string
    title?: LocalizedStr
    description?: LocalizedStr
    fields: ObjectId[]
}

export type Permission = {
    role: Role
    type: PermissionType
}


export type InputEnumItem = {
    identifier: string
    label: LocalizedStr
}

export type InputEnum = {
    id: ObjectId
    identifier: string
    items: InputEnumItem[]
}


export type InputField = {
    id: ObjectId
    identifier: string
    label?: LocalizedStr
    helpText?: LocalizedStr
    datatype: FieldType
    elements_type?: FieldType
    adt_enum_id?: ObjectId
    defaultValue?: string | number | boolean | null
    minValue?: number
    maxValue?: number
    useSlider?: boolean
    sliderMinLabel?: string
    sliderMaxLabel?: string
    sliderStepSize?: number
    unitString?: string
    qrCodeInput?: boolean
    displayPeriodDays?: number
    displayDayOne?: boolean
    maybeNull?: boolean
    permissions?: Permission[]
}

export const fieldTypeLabels = {
    [FieldType.StringType]: "Text Input",
    [FieldType.IntType]: "Integer/Rating",
    [FieldType.FloatType]: "Decimal",
    [FieldType.BoolType]: "Checkbox",
    [FieldType.TimeType]: "Time",
    [FieldType.DateType]: "Date",
    [FieldType.EnumType]: "Selection",
    [FieldType.ListType]: "List",
    [FieldType.FormType]: "Subform",
    [FieldType.Container]: "Container Start",
}

export const newInputEnum: InputEnum =
    {id: NEW_ELEMENT_ID, identifier: "", items: [
        {identifier: "", label: ""},
        {identifier: "", label: ""},
        {identifier: "", label: ""},
    ]}

export const newInputField : InputField = {
    id: NEW_ELEMENT_ID,
    identifier: "",
    label: "",
    datatype: FieldType.StringType,
    displayDayOne: true,
    sliderStepSize: 1,
}

export const newInputForm : InputForm = {
    id: NEW_ELEMENT_ID,
    identifier: "",
    fields: []
}
