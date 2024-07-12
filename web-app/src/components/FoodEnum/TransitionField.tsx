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

import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Autocomplete,
    Chip,
    IconButton,
    Paper,
    TextField
} from "@mui/material";
import {FC, useEffect, useState} from "react";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import "./FoodEnum.css";
import {FoodEnumTransition} from "../../models/main.ts";
import {AutocompleteEditField} from "../Editor/AutocompleteEditField.tsx";
import {FoodItemPreviewField} from "./FoodItemPreviewField.tsx";
import ArrowForwardIcon from '@mui/icons-material/ArrowForward';
import {useDataState} from "../../hooks/useState/useData.ts";
import DeleteIcon from "@mui/icons-material/Delete";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";

const FINISH_IDENTIFIER = "(Finish)";

const TagField = (props: {
                      label: string,
                      helpText: string,
                      options: string[],
                      value: string[],
                      setValue: (value: string[]) => void
                  }
) => (
    <Autocomplete
        multiple
        options={props.options}
        value={props.value}
        onChange={(_, value) => props.setValue(value)}
        freeSolo
        renderTags={(value: readonly string[], getTagProps) =>
            value.map((option: string, index: number) => (
                <Chip variant="outlined" label={option} {...getTagProps({index})} />
            ))
        }
        renderInput={(params) => (
            <TextField
                {...params}
                label={props.label}
                placeholder={"Add tag & press ENTER..."}
                helperText={props.helpText}
            />
        )}
    />
)

type TransitionFieldProps = {
    index?: number
    selectableItemIds: string[]
    transition: FoodEnumTransition
    setTransition: (transitionItem: FoodEnumTransition) => void
    onMoveUp?: () => void;
    onMoveDown?: () => void;
    onDelete?: () => void;
}


export const TransitionField: FC<TransitionFieldProps> = (
    {
        index,
        selectableItemIds,
        transition,
        setTransition,
        onMoveUp,
        onMoveDown,
        onDelete,
    }
) => {

    const hasTags = 
        !!transition.add_tags && transition.add_tags.length > 0 ||
        !!transition.require_tags && transition.require_tags.length > 0;

    const [tagsExpanded, setTagsExpanded] = useState<boolean>(hasTags)
    
    useEffect(() => {
        setTagsExpanded(hasTags);
    }, [transition, index]);

    const allFoodItems = useDataState(state => state.foodItems)
    
    const selectableItems = allFoodItems.filter(fi => selectableItemIds.includes(fi.id ?? ""));
    const selectedItem = transition.selected_item_id ? allFoodItems.find(fi => fi.id === transition.selected_item_id) : undefined;
    
    const allEnums = useDataState(state => state.foodEnums)
    const selectedTargetIdentifier = transition.target_enum ?
        allEnums.find(fe => fe.id === transition.target_enum)?.identifier :
        FINISH_IDENTIFIER;
    const selectableTargetIdentifiers =  [ FINISH_IDENTIFIER,  ...allEnums.map(fe => fe.identifier)];
    
    const selectableTagsSet = new Set<string>([]);
    allEnums.forEach(fe => {
        fe.transitions.forEach(t => {
            if (t.add_tags)
                t.add_tags.forEach(tag => selectableTagsSet.add(tag));
            if (t.require_tags)
                t.require_tags.forEach(tag => selectableTagsSet.add(tag));
        })
    });
    
    const update = (newTransitionItem: Partial<FoodEnumTransition>) => {
        setTransition({...transition, ...newTransitionItem})
    }
    

    return (
        <Paper className={"enum-transition"}>
            <div className={"side-by-side"}>
                <h4/>
            {/*<h4>{index !== undefined ? "Transition " + (index+1) : "Sample Transition"}</h4>*/}
                <IconButton disabled={!onDelete} onClick={() => onDelete && onDelete()}><DeleteIcon
                    fontSize={"small"}/></IconButton>
                <IconButton disabled={!onMoveUp} onClick={() => onMoveUp && onMoveUp()}><ArrowUpwardIcon
                    fontSize={"small"}/></IconButton>
                <IconButton disabled={!onMoveDown} onClick={() => onMoveDown && onMoveDown()}><ArrowDownwardIcon
                    fontSize={"small"}/></IconButton>
            </div>
            <div className={"side-by-side"}>
                <FoodItemPreviewField
                    useMatchAll={true}
                    label={"Selected Item"}
                    value={selectedItem}
                    items={selectableItems}
                    onChange={(item) => update({selected_item_id: item ? item.id : undefined})}
                />
                <ArrowForwardIcon/>
                <AutocompleteEditField
                    label={"Target"}
                    value={selectedTargetIdentifier}
                    onChange={(identifier) => {
                        if(identifier === FINISH_IDENTIFIER) 
                            update({target_enum: undefined})
                        
                        const targetEnum = allEnums.find(fe => fe.identifier === identifier)
                        update({target_enum: targetEnum ? targetEnum.id : undefined})
                    }}
                    optionsSource={selectableTargetIdentifiers}
                    freeSolo={false}/>
            </div>

            <Accordion expanded={tagsExpanded} onChange={(_, expanded) => setTagsExpanded(expanded)}>
                <AccordionSummary
                    expandIcon={<ExpandMoreIcon/>}
                    aria-controls="panel1a-content"
                    id="panel1a-header"
                >
                    <p>Conditions</p>
                </AccordionSummary>
                <AccordionDetails className={"enum-item-list"}>
                    <div className={"side-by-side"}>
                        <TagField label={"Required Tags"}
                                  helpText={"These tags must have been set before in the history for this transition to be considered."}
                                  options={[...selectableTagsSet]} value={transition.require_tags ?? []} setValue={(v => update({require_tags: v}))}/>

                        <TagField label={"Add Tags"}
                                  helpText={"These tags are set and can be referred to in the 'Required Tags' field in next transitions."}
                                  options={[...selectableTagsSet]} value={transition.add_tags ?? []} setValue={(v => update({add_tags: v}))}/>
                    </div>
                </AccordionDetails>
            </Accordion>


        </Paper>
    )
}