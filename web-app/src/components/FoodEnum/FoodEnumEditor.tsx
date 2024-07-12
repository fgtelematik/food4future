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

import {FoodEnum, FoodEnumItem} from "../../models/main.ts";
import {FC, useEffect, useState} from "react";
import {Accordion, AccordionDetails, AccordionSummary, FormControlLabel, Switch, TextField} from "@mui/material";
import {EditorDialog} from "../Editor/EditorDialog.tsx";
import useApi from "../../hooks/useApi";
import {useDataState} from "../../hooks/useState/useData.ts";
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';

import "./FoodEnum.css";
import {FoodEnumItemView} from "./FoodEnumItemView.tsx";
import {TransitionsEditor} from "./TransitionsEditor.tsx";
import {EditorProps} from "../generics.ts";


export const FoodEnumEditor: FC<EditorProps<FoodEnum>> = ({editItem, setEditItem, onSaveItem}) => {

    const foodEnums = useDataState(state => state.foodEnums);

    const [foodItemsExpanded, setFoodItemsExpanded] = useState(!(editItem?.item_ids.length === 0));
    const [transitionsExpanded, setTransitionsExpanded] = useState(true);

    const onConfirm = async () => {
        if (!editItem)
            return;

        const success = await onSaveItem(editItem);
        if (success)
            setEditItem(null);
    }


    // let previewImageUrl = "";
    // const imageFilename = editItem?.image?.filename;
    // if(typeof(imageFilename) === "string" && imageFilename !== "")
    // 	previewImageUrl = useApi().getImageUrl(imageFilename);

    const identifierAlreadyExists = !!editItem && foodEnums.some(fi => fi.identifier === editItem.identifier && fi.id !== editItem.id);

    const isValidInput =
        !!editItem && !!editItem.label && !!editItem.identifier && !identifierAlreadyExists;


    useEffect(() => {
        useApi().fetchFoodEnums()
    }, []);


    const allImages = useDataState(state => state.foodImages);

    const [currentItem, setCurrentItem] = useState<FoodEnumItem | undefined>(undefined);
    const [newItemKey, setNewItemKey] = useState<number>(0);

    useEffect(() => {
        setNewItemKey(prev => prev + 1);
    }, [editItem]);

    const updateIds = (updateFn: (prevIds: string[]) => string[]) => {
        if (!editItem)
            return;

        const newIds = updateFn(editItem.item_ids);
        setEditItem({...editItem, item_ids: newIds});
    }

    const addItem = (item_id: string) => {
        updateIds(prevIds => [...prevIds, item_id]);
    };

    const removeItem = (idx: number) => {
        updateIds(prevIds => prevIds.filter((_, i) => i !== idx));
    };

    const changeItem = (idx: number, item_id: string) => {
        updateIds(prevIds => prevIds.map((id, i) => i === idx ? item_id : id));
    };

    const moveItem = (idx: number, direction: "up" | "down") => {
        updateIds(prevIds => {
            const newIds = [...prevIds];
            const item = newIds.splice(idx, 1)[0];
            const targetIdx = direction === "up" ? idx - 1 : idx + 1;
            newIds.splice(targetIdx, 0, item);
            return newIds;
        });
    };

    const isAmount = editItem?.identifier?.toLowerCase().includes("amount") || false;

    const setIsAmount = (isAmount: boolean) => {
        if (editItem?.identifier === undefined)
            return;

        let newIdentifier = editItem.identifier;

        if(isAmount && !newIdentifier.startsWith("Amount")) {
            let prefix = "Amount";
            if(newIdentifier=="" || (newIdentifier.length > 0 && newIdentifier[0] !== "_"))
                prefix += "_";
            newIdentifier = prefix + newIdentifier;
        }
        else if(!isAmount) {
            // replace "amount_" and "amount" (case insensitive) with "":
            newIdentifier = newIdentifier.replace(/amount_?/i, "");
        }

        setEditItem({...editItem, identifier: newIdentifier});
    }


    return (
        <>

            <EditorDialog
                open={editItem !== null}
                title={(!!editItem?.id ? "Edit" : "Create") + " Food Screen"}
                onSubmit={() => onConfirm()}
                onCancel={() => setEditItem(null)}
                isValidContent={isValidInput}
            >

                <TextField
                    label={"Screen Identifier*"} value={editItem?.identifier ?? ""}
                    error={identifierAlreadyExists}
                    helperText={identifierAlreadyExists ? "There is already a food screen with this identifier" : undefined}
                    onChange={(e) => setEditItem({...editItem!, identifier: e.target.value})}/>

                <TextField
                    label={"Screen Title*"} value={editItem?.label ?? ""}
                    onChange={(e) => setEditItem({...editItem!, label: e.target.value})}/>

                <TextField
                    label={"Instructional Text"} value={editItem?.help_text ?? ""}
                    multiline
                    rows={5}
                    onChange={(e) => setEditItem({...editItem!, help_text: e.target.value})}/>


                <FormControlLabel
                    control={
                        <Switch
                            checked={isAmount}
                            onChange={(e) => setIsAmount(e.target.checked)}
                            color="primary"/>
                    }
                    label={<>This is an Amount Selection<br/><span className={"additional-info"}>Amounts will be displayed at the <i>front</i> in the food consumption summary, i.e.: "<b>300 ml</b> Apple Juice"</span></>}

                />

                <Accordion expanded={foodItemsExpanded} onChange={(_, expanded) => setFoodItemsExpanded(expanded)}>
                    <AccordionSummary
                        expandIcon={<ExpandMoreIcon/>}
                    >
                        <p><b>Food Items</b></p>
                    </AccordionSummary>
                    <AccordionDetails className={"enum-item-list"}>
                        {

                            editItem?.item_ids.map((item_id, idx) => (
                                    <FoodEnumItemView
                                        key={item_id + "_" + idx}
                                        item_id={item_id}
                                        index={idx}
                                        onChange={(new_id) => changeItem(idx, new_id)}
                                        onDelete={() => removeItem(idx)}
                                        onMoveUp={idx == 0 ? undefined : () => moveItem(idx, "up")}
                                        onMoveDown={idx == editItem.item_ids.length - 1 ? undefined : () => moveItem(idx, "down")}
                                    />
                                )
                            )
                        }
                        <FoodEnumItemView key={"newItem" + newItemKey} item_id={null} onChange={addItem}/>
                    </AccordionDetails>
                </Accordion>

                <Accordion expanded={transitionsExpanded} onChange={(_, expanded) => setTransitionsExpanded(expanded)}>
                    <AccordionSummary
                        expandIcon={<ExpandMoreIcon/>}
                        aria-controls="panel1a-content"
                        id="panel1a-header"
                    >
                        <p><b>Transitions</b></p>
                    </AccordionSummary>
                    <AccordionDetails className={"enum-item-list"}>
                        {
                            null
                        }
                        {editItem &&
                            <TransitionsEditor
                                foodEnum={editItem}
                                setFoodEnum={setEditItem}
                            />
                        }
                    </AccordionDetails>
                </Accordion>


            </EditorDialog>

        </>
    )
}