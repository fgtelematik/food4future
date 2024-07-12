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
import {FormControl, FormControlLabel, FormLabel, Radio, RadioGroup, TextField} from "@mui/material";
import React, {useEffect} from "react";
import {toIsoString} from "../../utils.ts";

enum RadioValue {
    Specific = "Specific",
    Momentary = "Momentary",
    None = "None"
}

export const InputDefaultDateTimeValue = (props: {
    maybeNull: boolean,
    datatype: FieldType.DateType | FieldType.TimeType,
    disabled?: boolean,
    label: string,
    value: any,
    setValue: (value: any) => void,
}) => {
    const dateTimeString = props.datatype == FieldType.DateType ? "Date" : "Time";
    const dateTimeUnitString = props.datatype == FieldType.DateType ? "days" : "seconds";
    const dateTimeMomentaryIdentifier = props.datatype == FieldType.DateType ? "today" : "now";
    const matchesAnyMomentaryIdentifier = /^(now|today)([\\+\-])(\d+)$/.test(props.value ? props.value.toString() : "");

    const isMomentaryDateTime =
        (props.datatype === FieldType.DateType || props.datatype == FieldType.TimeType) &&
        !!props.value && props.value.toString().startsWith(dateTimeMomentaryIdentifier);

    let dateTimeOffset = 0;
    if (isMomentaryDateTime && !!props.value) {  // extract e.g. int offset -3 from a string: "today-3"
        const possibleOffset = parseInt(props.value.toString().substring(dateTimeMomentaryIdentifier.length));
        if (!isNaN(possibleOffset))
            dateTimeOffset = possibleOffset;
    }

    const setDateTimeOffset = (offsetStr: string) => {
        let newOffset = parseInt(offsetStr);
        if (isNaN(newOffset))
            newOffset = 0;

        let newValue = dateTimeMomentaryIdentifier;

        if (newOffset > 0)
            newValue += "+" + newOffset;
        else if (newOffset < 0)
            newValue += newOffset;

        props.setValue(newValue);
    }


    const onSetTime = (timeStr: string) => {
        const d = new Date();
        const [hours, minutes] = timeStr.split(":").map(Number);

        d.setHours(hours, minutes);

        props.setValue(toIsoString(d))
    }

    const onSetDate = (dateStr: string) => {
        const d = new Date();

        const [year, month, day] = dateStr.split("-").map(Number);
        d.setFullYear(year, month-1 , day);

        props.setValue(toIsoString(d))
    }

    let currentTime = "";
    let currentDate = "";

    let dateObj = new Date(props.value);

    let radioValue: RadioValue | null = null;
    if (!props.value)
        radioValue = RadioValue.None
    else if (isMomentaryDateTime) {
        radioValue = RadioValue.Momentary;
    } else if (!isNaN(Number(dateObj)) && isNaN(props.value) && !matchesAnyMomentaryIdentifier) {
        // valid ISO Date/Time string (used by server for both Date or Time values)

        radioValue = RadioValue.Specific;
        const hours = dateObj.getHours().toString().padStart(2,"0");
        const minutes = dateObj.getMinutes().toString().padStart(2,"0");
        currentTime = hours +":"+minutes;

        const year = dateObj.getFullYear().toString();
        const month = (dateObj.getMonth() + 1).toString().padStart(2, "0");
        const day = (dateObj.getDate()).toString().padStart(2, "0");
        currentDate = year + "-" + month + "-" + day;
    }

    useEffect(() => {
        if (radioValue == null) {
            // invalid default value for the selected datatype.
            // auto-change to undefined:
            props.setValue(undefined);
        }
    }, [radioValue]);

    const onChangeRadio = (radioValue: RadioValue) => {
        switch (radioValue) {
            case RadioValue.None:
                props.setValue(undefined);
                break;
            case RadioValue.Specific:
                props.setValue(toIsoString(new Date()))
                break;
            case RadioValue.Momentary:
                props.setValue(dateTimeMomentaryIdentifier);
        }
    }


    return <FormControl
    >
        <FormLabel>{props.label}</FormLabel>
        <RadioGroup
            value={radioValue}
            onChange={(e) => onChangeRadio(e.target.value as RadioValue)}
        >
            <div className={"field-row text-with-fields "} style={{margin: 0}}>
                    <FormControlLabel value={RadioValue.Specific} control={<Radio disabled={props.disabled}/>}
                                      label={"Specific " + dateTimeString + ":"}/>
                    {
                        props.datatype == FieldType.TimeType &&
                        <TextField
                            disabled={props.disabled || radioValue != RadioValue.Specific}
                            label={props.label}
                            type={"time"}
                            style={{width: "10em"}}
                            InputLabelProps={{shrink: true}}
                            value={currentTime}
                            onChange={(e) => onSetTime(e.target.value)}
                        />
                    }

                    {
                        props.datatype == FieldType.DateType &&
                        <TextField
                            disabled={props.disabled || radioValue != RadioValue.Specific}
                            label={props.label}
                            type={"date"}
                            style={{width: "10em"}}
                            InputLabelProps={{shrink: true}}
                            value={currentDate}
                            onChange={(e) => onSetDate(e.target.value)}
                        />
                    }
            </div>
            <div className={"field-row text-with-fields"} style={{margin: 0}}>
                <FormControlLabel value={RadioValue.Momentary} control={<Radio disabled={props.disabled}/>}
                                  label={"Momentary " + dateTimeString + ""}/>
                <p>
                    with an offset of&nbsp;
                    <TextField
                        disabled={props.disabled || radioValue != RadioValue.Momentary}
                        hiddenLabel
                        className={""}
                        style={{width: "5em"}}
                        type={"number"}
                        value={dateTimeOffset}
                        onChange={(e) => setDateTimeOffset(e.target.value)}
                        InputProps={{
                            disableUnderline: true,
                        }}
                        inputProps={{
                            style: {fontWeight: "bold", textAlign: "right",},
                        }}
                        size={"small"}
                        variant="filled"
                        onFocus={(e) => e.target.select()}
                    />
                    &nbsp;{dateTimeUnitString}
                </p>
            </div>
            <div className={"field-row text-with-fields"} style={{margin: 0}}>
                <FormControlLabel value={RadioValue.None} control={<Radio disabled={props.disabled}/>}
                                  label={"No value"}/>
            </div>
        </RadioGroup>
    </FormControl>
}