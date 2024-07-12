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

import {FieldType} from "../../models/form.ts";
import {ObjectId} from "../../models/main.ts";
import React, {ReactElement, useEffect} from "react";
import {Autocomplete, FormControlLabel, FormGroup, Switch, TextField} from "@mui/material";
import {InputDefaultDateTimeValue} from "./InputDefaultDateTimeValue.tsx";
import {useDataState} from "../../hooks/useState/useData.ts";
import {locStr} from "../../utils.ts";

export const InputDefaultValueField = (props: {
    maybeNull: boolean,
    datatype: FieldType,
    enum_id?: ObjectId,
    disabled?: boolean,
    label?: string,
    value: string | number | boolean | null | undefined,
    setValue: (value: any) => void,
}) => {


    useEffect(() => {
        const res = setValue(props.value);
        if (!res || props.datatype == FieldType.BoolType)
            setValue(null);
    }, [props.datatype]);

    const label = props.label ?? "Default Value";

    const inputEnums = useDataState(state => state.inputEnums);
    const inputEnum = !!props.enum_id ? inputEnums.find(e => e.id === props.enum_id) : undefined;
    const currentEnumValue = !!inputEnum && !!props.value &&
        inputEnum.items.find(it=>it.identifier === props.value) || null;


    const setValue = (newValue: any) => {
        if (!newValue)
            props.setValue(undefined);

        try {
            if (props.datatype == FieldType.IntType) {
                newValue = parseInt(newValue);
                if (isNaN(newValue)) {
                    return false;
                }
            } else if (props.datatype == FieldType.FloatType) {
                newValue = parseFloat(newValue);
                if (isNaN(newValue)) {
                    return false;
                }
            } else if(props.datatype == FieldType.EnumType) {
                if(!inputEnum || !inputEnum.items.map(it=>it.identifier).includes(newValue))
                    newValue = undefined;
            }

        } catch (e) {
            return false;
        }

        props.setValue(newValue);
        return true;
    }


    let res: ReactElement | undefined = <></>;

    switch (props.datatype) {
        case FieldType.ListType:
        case FieldType.FormType:
            res = <TextField
                disabled={true}
                label={label}
                value={props.datatype === FieldType.ListType ? "(Empty List)" : "(Empty Form Data)"}
                helperText={"Custom default values for lists and forms are currently not supported."}
            />;
            break;
        case FieldType.BoolType:
            res = <FormGroup>
                <FormControlLabel control={<Switch
                    checked={!!props.value}
                    disabled={props.disabled}
                    onChange={(e) => props.setValue(e.target.checked)}
                />} label={"Switch on by default"}/>
            </FormGroup>
            break;
        case FieldType.IntType:
        case FieldType.FloatType:
            res = <TextField
                disabled={props.disabled}
                label={label}
                type={"number"}
                value={props.value ?? ""}
                onChange={(e) => setValue(e.target.value || null)}
            />
            break;
        case FieldType.StringType:
            res = <TextField
                disabled={props.disabled}
                label={label}
                type={"text"}
                value={props.value ?? ""}
                onChange={(e) => setValue(e.target.value || null)}
            />
            break;

        case FieldType.TimeType:
        case FieldType.DateType:
            res = <InputDefaultDateTimeValue
                label={label}
                maybeNull={props.maybeNull}
                datatype={props.datatype}
                value={props.value}
                setValue={props.setValue}/>
            break;
        case FieldType.EnumType:
            res = inputEnum &&
                <Autocomplete
                    value={currentEnumValue}
                    onChange={(_, value) => props.setValue(value?.identifier || undefined)}
                    disabled={props.disabled}
                    renderInput={(params) =>
                        <TextField
                            {...params}
                            label={label}
                        />
                    }
                    options={inputEnum.items}
                    getOptionLabel={it => it.identifier}
                    renderOption={(props, option) =>
                        <li {...props}>
                            <p>{option.identifier}<br/>
                                <span className={"additional-info"}>{locStr(option.label)}</span>
                            </p>
                        </li>
                    }

                />

    }
    return res;
}
