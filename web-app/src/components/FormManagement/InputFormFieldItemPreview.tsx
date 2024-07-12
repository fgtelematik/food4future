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

import {Autocomplete, Button, Chip, createFilterOptions, InputAdornment, TextField} from "@mui/material";
import {FoodEnumItem} from "../../models/main.ts";
import {locStr} from "../../utils.ts";
import EditIcon from '@mui/icons-material/Edit';
import AddIcon from '@mui/icons-material/Add';

import useApi from "../../hooks/useApi";
import {useGetFullInputFieldTypeLabel} from "../../hooks/other_hooks.tsx";


const filterOptions = createFilterOptions<InputField>({
    stringify: (option) => locStr(option.label) + " " + (option.identifier ?? "")
});

export const InputFormFieldItemPreview = (props: {
    label: string,
    value: InputField | undefined,
    onChange: (value: InputField | undefined) => void,
    items: InputField[],
    action?: "add" | "edit",
    onAction?: () => void,
    errorText?: string,
}) => {

    const value = props.value;

    const getInputFieldTypeLabel = useGetFullInputFieldTypeLabel();

    return (
        <Autocomplete
            filterOptions={filterOptions}
            disableClearable={true}
            key={props.label}
            options={props.items}
            noOptionsText={"No unused fields available"}
            getOptionLabel={(item) => locStr(item.label) || item.identifier}
            value={value}
            onChange={(_, value) => props.onChange(value)}
            isOptionEqualToValue={(option, value) => !value ? false : option.id === value.id}

            renderInput={(params) => (<>

                    <TextField
                        {...params}
                        error={!!props.errorText}
                        label={props.label}
                        InputProps={{
                            ...params.InputProps,
                            endAdornment: !!props.action && !!props.onAction && (
                                props.action == "add" ?
                                    <Chip icon={<AddIcon/>} label={"Create Field…"} onClick={props.onAction}/>
                                    :
                                    <Chip icon={<EditIcon/>} label={"Edit…"} onClick={props.onAction}/>
                            )
                        }}
                        helperText={
                            <p>
                                {!!props.errorText && <p>{props.errorText}</p>}
                            </p>
                        }
                    /></>
            )}
            renderOption={(props, item, state) => (
                <li {...props} className={"image-dropdown-preview-container" + (state.selected ? " active" : "")}
                    key={item.id}>
                    {
                        <p>{locStr(item.label)}&nbsp;
                            <span className={"additional-info"}>| {item.identifier}</span>
                            <br/>
                            <span className={"additional-info"}>{getInputFieldTypeLabel(item)}</span>
                        </p>
                    }
                </li>)}
        />
    )
}