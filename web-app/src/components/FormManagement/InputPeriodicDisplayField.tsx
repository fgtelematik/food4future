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

import {InputField} from "../../models/form.ts";
import {Autocomplete, Card, FormControlLabel, FormGroup, Switch, TextField} from "@mui/material";
import React from "react";

export const InputPeriodicDisplayField = (props: {
    field: InputField,
    updateField: (field: InputField) => void,
}) => {

    const setDisplayPeriodDays = (days: number | null) => {
        let newValue: number | undefined;
        if(days === null || isNaN(Number(days)) || Number(days) < 1)
            newValue = undefined;
        else
            newValue = Number(days);

        const newField = {...props.field};
        newField.displayPeriodDays = newValue;
        props.updateField(newField);
    }

    const setDisplayDayOne = (value: boolean) => {
        const newField = {...props.field};
        newField.displayDayOne = value;
        props.updateField(newField);
    }

    const isDisplayPeriodDays = !!props.field.displayPeriodDays && props.field.displayPeriodDays > 0;

    let pluralSuffix = "th";
    if (props.field.displayPeriodDays == 1)
        pluralSuffix = "st";
    else if (props.field.displayPeriodDays == 2)
        pluralSuffix = "nd";
    else if (props.field.displayPeriodDays == 3)
        pluralSuffix = "rd";

    return (
        <Card>
            <div className={"text-with-fields"}>
            <FormGroup>
                <FormControlLabel control={<Switch
                    checked={isDisplayPeriodDays}
                    onChange={(_, checked) => setDisplayPeriodDays(checked ? 2 : null)}
                />}
                                  label={<p><b>Periodic display</b> - show this field on <i>specific</i> study days
                                  </p>}/>
            </FormGroup>

            {isDisplayPeriodDays &&
                <p>
                    <span>Display every&nbsp;</span>
                    <TextField
                        hiddenLabel
                        className={"periodic-display-days-input"}
                        type={"number"}
                        value={props.field.displayPeriodDays}
                        onChange={(e) => setDisplayPeriodDays(parseInt(e.target.value))}
                        InputProps={{
                            disableUnderline: true,
                            endAdornment: <b>{pluralSuffix}</b>
                        }}
                        inputProps={{
                            min: 1,
                            style: {fontWeight: "bold", textAlign: "right",},
                        }}
                        size={"small"}
                        variant="filled"
                        onFocus={(e) => e.target.select()}
                    />
                    <span>study day,&nbsp;</span>
                    <Autocomplete
                        disableClearable={true}
                        size={"small"}
                        renderInput={(params) =>
                            <TextField
                                {...params}
                                hiddenLabel
                                className={"periodic-display-day-one-input"}
                                size={"small"}
                                variant="filled"
                                InputProps={{
                                    ...params.InputProps,
                                    disableUnderline: true,
                                    style: {fontWeight: "bold", textAlign: "right", padding: "0"},
                                }}
                            />}
                        options={["including", "excluding"]}
                        value={props.field.displayDayOne ? "including" : "excluding"}
                        onChange={(_, value) => setDisplayDayOne(value == "including")}
                    />
                    <span> the first study day.</span>
                </p>
            }
            </div>
        </Card>
    )
}