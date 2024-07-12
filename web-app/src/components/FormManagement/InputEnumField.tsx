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

import {Autocomplete, Button, createFilterOptions, TextField} from "@mui/material";
import {ObjectId} from "../../models/main.ts";

import "./FormManagement.css"
import {InputEnum} from "../../models/form.ts";
import {useDataState} from "../../hooks/useState/useData.ts";
import {ReactNode, useState} from "react";
import {InputEnumManageDialog} from "./InputEnumManageDialog.tsx";


const filterOptions = createFilterOptions<InputEnum>({
    stringify: (option) => option.identifier,
});


export const InputEnumField = (props: {
    enumId: ObjectId | null
    onChange: (newEnumId: ObjectId | null) => void,
    label?: string,
    disabled?: boolean,
    helperText?: ReactNode,
    error?: boolean,

}) => {

    const [showEditor, setShowEditor] = useState<boolean>(false);

    const allEnums = useDataState(state => state.inputEnums);

    let selectedEnumSearchResult = props.enumId == null ? null : allEnums.find(fi => fi.id === props.enumId);
    let isEnumNotFound = selectedEnumSearchResult === undefined;
    const selectedEnum = selectedEnumSearchResult || null;

    return (
        <div className={"enum-preview-field-container"}>
            <Autocomplete
                filterOptions={filterOptions}
                key={props.label}
                disabled={props.disabled}
                options={allEnums}
                getOptionLabel={(item) => item.identifier}
                value={selectedEnum}
                noOptionsText={"No selections available yet."}
                onChange={(_, value) => props.onChange(value?.id ?? null)}
                isOptionEqualToValue={(option, value) => !value ? false : option.id === value.id}
                renderInput={(params) => (<>
                        <TextField
                            {...params}
                            error={props.error || isEnumNotFound}
                            label={props.label}
                            helperText={isEnumNotFound ? "The assigned enum could not be found." : props.helperText}
                            placeholder={isEnumNotFound ? "(missing ID: "+ props.enumId+")" : undefined}
                            InputLabelProps={{shrink: isEnumNotFound || undefined}}
                        /></>
                )}
                renderOption={(props, option) => (
                    <li {...props}>
                        <p>{option.identifier}<br/><span className={"additional-info no-word-wrap"}>{option.items.map(i=>i.label).join(", ")}</span></p>
                    </li>
                )}
            />
            <Button
                disabled={props.disabled}
                onClick={() => setShowEditor(true)}
                style={{textAlign: "left"}}
            >
                Manage Selections...
            </Button>

            <InputEnumManageDialog enumId={props.enumId}
                             onChange={props.onChange}
                             showEditor={showEditor}
                             setShowEditor={setShowEditor}/>
        </div>
    )
}