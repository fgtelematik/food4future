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

import {CircularProgress, debounce, TextField, TextFieldProps} from "@mui/material";
import {useState} from "react";
import CheckIcon from "@mui/icons-material/Check";

export type ServerValidatedTextFieldProps<T> = TextFieldProps & {
    updateValidationResult: (result: T | undefined) => void;
    validateFn: (value: string) => Promise<T>
    isPositiveValidationResult: boolean;
}

let debounceFn : ReturnType<typeof debounce> | undefined;

export function ServerValidatedTextField<T>(
    props: ServerValidatedTextFieldProps<T>
) {
    const {
        updateValidationResult,
        validateFn,
        isPositiveValidationResult,
        ...textFieldProps } = props;

    const [isValidationInProgress, setIsValidationInProgress] = useState(false);

    const origOnChange = textFieldProps.onChange;

    function onChange(event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) {
        if(!event.target)
            return;

        const value = event.target.value;

        if(debounceFn)
            debounceFn.clear();

        // Set validation result to undefined while validating
        updateValidationResult(undefined);
        setIsValidationInProgress(true);

        const debounced = debounce(async () => {
            const result = await validateFn(value);
            updateValidationResult(result);
            setIsValidationInProgress(false);
        }, 400);

        debounceFn = debounced;
        debounced();

        if(origOnChange)
            origOnChange(event);
    }

    return (
        <TextField
            {...textFieldProps}
            onChange={onChange}
            InputProps={
            {
                ...textFieldProps.InputProps,
                endAdornment:
                isValidationInProgress && <CircularProgress size={"1.5em"}/> ||
                !isValidationInProgress && isPositiveValidationResult && <CheckIcon style={{color: "green"}}/>

            }
            }

        ></TextField>
    );
}