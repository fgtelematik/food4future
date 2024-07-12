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

import {Autocomplete, TextField} from "@mui/material";
import React from "react";

export const AutocompleteEditField = (props: {
    label: string,
    value: string | undefined,
    onChange: (value: string | undefined) => void,
    optionsSource: (string | undefined)[],
    type?: string,
    helperText?: React.ReactNode,
    freeSolo?: boolean,
    disabled?: boolean
}) => {
    const options = [...new Set(
        props.optionsSource.filter(s => s && s !== ""))]
            .sort((a, b) => a!.localeCompare(b!));
    
    return (
        <Autocomplete
            disabled={props.disabled}
            freeSolo={props.freeSolo || props.freeSolo === undefined}
            key={props.label}
            disableClearable
            options={options}
            value={props.value}
            onChange={(_, value) => props.onChange(value)}
            renderInput={(params) => (
                <TextField
                    {...params}
                    type={props.type ?? "text"}
                    onChange={(e) => props.onChange(e.target.value)}
                    label={props.label}
                    helperText={props.helperText}
                    InputProps={{
                        ...params.InputProps,
                    }}
                />
            )}
        />
    )
}