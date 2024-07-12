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

import {FC, useEffect, useState} from "react";
import {StudyManagementSectionProps} from "./interface.ts";
import {Autocomplete, createFilterOptions, TextField} from "@mui/material";
import {useDataState} from "../../hooks/useState/useData.ts";
import {AutocompleteEditField} from "../Editor/AutocompleteEditField.tsx";
import {FoodEnum, FoodEnumItem} from "../../models/main.ts";
import {locStr} from "../../utils.ts";
import {InputForm} from "../../models/form.ts";
import {Link} from "@tanstack/react-router";
import {foodEnumsRoute, inputFormsRoute} from "../../routers/MainRouter.tsx";


const foodEnumFilterOptions = createFilterOptions<FoodEnum>({
    stringify: (option) => locStr(option.label) + " " + (option.identifier ?? "")
});

const inputFormFilterOptions = createFilterOptions<InputForm>({
    stringify: (option) => locStr(option.title) + " " + (option.identifier ?? "")
});

export const GeneralStudyManagementSection: FC<StudyManagementSectionProps> = ({study, updateStudy}) => {
    const studies = useDataState(state => state.studies);
    const [studyExistsError, setStudyExistsError] = useState<boolean>(studies.some(s => study.id !== s.id && locStr(s.title).trim() === locStr(study.title).trim()));
    const [noTitleError, setNoTitleError] = useState<boolean>(false);
    const allEnums = useDataState(state => state.foodEnums);
    const currentInitialFoodEnum = allEnums.find(e => e.id === study.initial_food_enum);

    const inputForms = useDataState(state => state.inputForms);
    const currentStaticDataForm = inputForms.find(e => e.id === study.static_data_form);
    const currentUserDataForm = inputForms.find(e => e.id === study.user_data_form);

    
    const error = studyExistsError || noTitleError;
    
    const renderFormPreview = (props: Object, item: InputForm, state: Object) => (
        <li {...props}>
            <p>{item.identifier}<br/>
                <span className={"additional-info"}>{locStr(item.title)}</span></p>
        </li>
    );

    const onUpdateTitle = (title: string, validateOnly = false) => {
        const studyExists = studies.some(s => study.id !== s.id && locStr(s.title).trim() === title.trim());
        setStudyExistsError(studyExists);
        setNoTitleError(!title);
        
        if(!validateOnly)
            updateStudy({...study, title}, !!title && !studyExists);
    }

    useEffect(() => {
        onUpdateTitle(locStr(study.title) ?? "", true);
    }, []);
    
    const onUpdateDefaultRuntime = (runtimeStr: string) => {
        let runtime = parseInt(runtimeStr);
        if(runtime < 1 || isNaN(runtime)) 
            runtime = 1;
        updateStudy({...study, default_runtime_days: runtime}, !isNaN(runtime));
    }

    const titleHelperText = studyExistsError ? "Study with this name already exists" : "Study name is required";
    
    const initialFoodEnumHelperText =
        <>Select the food screen, participants will see first when they add a consumption.<br/>
            Go to <Link to={foodEnumsRoute.id} params={{}}  search={{}}>Food Screens</Link> to manage available screens.
        </>;

    const userDataFormHelperText =
        <>Select the form which is used as the Daily Questions form displayed to the participant.<br/>
            Go to <Link to={inputFormsRoute.id} params={{}}  search={{}}>Question Forms</Link> to manage available forms.
        </>;
    const staticDataFormHelperText =
        <>Select the form, staff will see to enter participant data and participants can see in their profile view.<br/>
            Go to <Link to={inputFormsRoute.id} params={{}}  search={{}}>Question Forms</Link> to manage available forms.
        </>;

    return (
        <>
            <TextField
                label={"Study Name*"} 
                value={study?.title ?? ""}
                error={error}
                helperText={error ? titleHelperText : undefined}
                onFocus={event => {
                    event.target.select();
                }}
                onChange={(e) => onUpdateTitle(e.target.value)}/>

            <Autocomplete
                filterOptions={foodEnumFilterOptions}
                freeSolo={false}
                options={allEnums}
                value={currentInitialFoodEnum ?? null}
                getOptionLabel={(option) => option.identifier ?? ""}
                onChange={(_, value) => updateStudy({...study, initial_food_enum: value?.id}, !error)}
                isOptionEqualToValue={(option, value) => !value ? false : option?.id === value.id}
                renderInput={(params) => (
                    <TextField
                        {...params}
                        type={"text"}
                        label={"Initial Food Selection Screen"}
                        helperText={initialFoodEnumHelperText}
                        InputProps={{
                            ...params.InputProps,
                        }}
                    />
                )}
                renderOption={(props, item, state) => (
                    <li {...props}>
                        <p>{locStr(item.label)}<br/>
                            <span className={"additional-info"}>{item.identifier}</span></p>
                    </li>
                )}
            />

            <Autocomplete
                filterOptions={inputFormFilterOptions}
                freeSolo={false}
                options={inputForms}
                value={currentStaticDataForm ?? null}
                getOptionLabel={(option) => option.identifier ?? ""}
                onChange={(_, value) => updateStudy({...study, static_data_form: value?.id}, !error)}
                isOptionEqualToValue={(option, value) => !value ? false : option?.id === value.id}
                renderInput={(params) => (
                    <TextField
                        {...params}
                        type={"text"}
                        label={"Static User Data Form"}
                        helperText={staticDataFormHelperText}
                        InputProps={{
                            ...params.InputProps,
                        }}
                    />
                )}
                renderOption={renderFormPreview}
            />

            <Autocomplete
                filterOptions={inputFormFilterOptions}
                freeSolo={false}
                options={inputForms}
                value={currentUserDataForm ?? null}
                getOptionLabel={(option) => option.identifier ?? ""}
                onChange={(_, value) => updateStudy({...study, user_data_form: value?.id}, !error)}
                isOptionEqualToValue={(option, value) => !value ? false : option?.id === value.id}
                renderInput={(params) => (
                    <TextField
                        {...params}
                        type={"text"}
                        label={"Daily Questions Form"}
                        helperText={userDataFormHelperText}
                        InputProps={{
                            ...params.InputProps,
                        }}
                    />
                )}
                renderOption={renderFormPreview}
            />


            <TextField
                label={"Default runtime (in days)"}
                type={"number"}
                value={study?.default_runtime_days}
                onChange={(e) => onUpdateDefaultRuntime(e.target.value)}
                helperText= {<>Specify the pre-set study runtime for a newly added participant. The runtime can be adjusted individually per participant.</>}
            />
            
        </>
    )
}