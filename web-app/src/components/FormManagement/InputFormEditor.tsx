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

import React, {FC, useEffect, useState} from "react";
import {EditorDialog} from "../Editor/EditorDialog.tsx";
import {EditorProps} from "../generics.ts";
import {
    FieldType,
    IdentifierCheckResult,
    identifierHelperText,
    InputField,
    InputForm,
    newInputField
} from "../../models/form.ts";
import {NEW_ELEMENT_ID, useDataState} from "../../hooks/useState/useData.ts";
import {ServerValidatedTextField} from "../Editor/ServerValidatedTextField.tsx";
import useApi from "../../hooks/useApi";
import {Accordion, AccordionDetails, TextField} from "@mui/material";
import {InputFormFieldItem} from "./InputFormFieldItem.tsx";
import {InputFieldEditor} from "./InputFieldEditor.tsx";
import {ObjectId} from "../../models/main.ts";

export const InputFormEditor : FC<EditorProps<InputForm>> = ({editItem, setEditItem, onSaveItem}) => {

    const allInputFields = useDataState(state => state.inputFields);
    const unusedInputFields = allInputFields.filter(field => !editItem?.fields.includes(field.id));

    const [identifierValidationResult, setIdentifierValidationResult] = useState<IdentifierCheckResult | undefined>(IdentifierCheckResult.OK);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [originalIdentifier, setOriginalIdentifier] = useState<string | undefined>(undefined);

    const [editField, setEditField] = useState<InputField | null>(null);
    const [editFieldMode, setEditFieldMode] = useState<"edit" | "create">("create");

    const isNew = !editItem || editItem?.id == NEW_ELEMENT_ID;

    const isValidIdentifier =
        identifierValidationResult == IdentifierCheckResult.OK ||
        identifierValidationResult == IdentifierCheckResult.AlreadyInUse && editItem?.identifier == originalIdentifier

    const [newItemKey, setNewItemKey] = useState<number>(0);

    const isContainerField = (field_id: ObjectId) => allInputFields.find(field => field.id === field_id)?.datatype == FieldType.Container;

    const multipleIds = editItem?.fields.filter((id, idx) => !isContainerField(id) && editItem.fields.indexOf(id) !== idx) || [];

    const isValidInput =
        !!editItem?.identifier &&
        isValidIdentifier &&
        multipleIds.length === 0;


    useEffect(() => {
        setNewItemKey(prev => prev + 1);
    }, [editItem]);


    useEffect(() => {
        if(editItem)
            useApi().fetchAllFormData();
    }, [editItem?.id]);


    useEffect(() => {
        if (editItem)
            setOriginalIdentifier(editItem.identifier);
        else
            setOriginalIdentifier(undefined);

        if (!editItem)
            return;

    }, [editItem?.id]);


    const onConfirm = async () => {
        if(!editItem || !isValidInput)
            return;
        let success = false;

        setIsSubmitting(true);
        try {
            success = await onSaveItem(editItem);
        } finally {
            setIsSubmitting(false);
        }

        if(success)
            setEditItem(null);
    }

    const onConfirmEditField = async (field: InputField) => {
        if(!editField)
            return false;

         await useApi().upsertInputField(field).then(() => {});

         if(editFieldMode == "create") {
             setEditItem({...editItem!, fields: [...editItem!.fields, field.id]});
         }

         return true;
    }

    const updateIds = (updateFn: (prevIds: string[]) => string[]) => {
        if (!editItem)
            return;

        const newIds = updateFn(editItem.fields);
        setEditItem({...editItem, fields: newIds});
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

    const moveItem = (idx: number, direction: "up" | "down")  => {
        updateIds(prevIds => {
            const newIds = [...prevIds];
            const item = newIds.splice(idx, 1)[0];
            const targetIdx = direction === "up" ? idx-1 : idx+1;
            newIds.splice(targetIdx, 0, item);
            return newIds;
        });
    };


    return (
        <>

            <EditorDialog
                open={editItem !== null}
                title={(isNew ? "Create" : "Edit") + " Question Form"}
                onSubmit={() => onConfirm()}
                onCancel={() => setEditItem(null)}
                isValidContent={isValidInput}
            >
                <ServerValidatedTextField
                    validateFn={useApi().checkInputFormIdentifier}
                    updateValidationResult={setIdentifierValidationResult}
                    isPositiveValidationResult={!!editItem?.identifier && identifierValidationResult == IdentifierCheckResult.OK}
                    disabled={isSubmitting}
                    value={editItem?.identifier ?? ""}
                    onChange={(e) => setEditItem({...editItem!, identifier: e.target.value})}
                    label={"Form Identifier*"}
                    error={!!identifierValidationResult && !isValidIdentifier}
                    helperText={isValidIdentifier ? "" : (identifierValidationResult && identifierHelperText[identifierValidationResult])}
                />

                <TextField
                    label={"Form Title"}
                    helperText={"This text will be displayed as title in the Action Bar."}
                    value={editItem?.title ?? ""}
                    onChange={(e) => setEditItem({...editItem!, title: e.target.value})}
                    disabled={isSubmitting}
                />

                <TextField
                    label={"Instructional Text"}
                    multiline={true}
                    rows={4}
                    helperText={"This text will be displayed above the form."}
                    value={editItem?.description ?? ""}
                    onChange={(e) => setEditItem({...editItem!, description: e.target.value})}
                    disabled={isSubmitting}
                />

                <Accordion expanded={true}>

                    <AccordionDetails className={"enum-item-list"}>
                        <h2><b>Form Fields</b></h2>

                        {
                            editItem?.fields.map((field_id, idx) => {
                                    const field= allInputFields.find(field => field.id === field_id);

                                    return (
                                        <InputFormFieldItem
                                            options={allInputFields}
                                            key={field_id + "_" + idx}
                                            item_id={field_id}
                                            index={idx}
                                            errorText={multipleIds.includes(field_id) && "This field is used multiple times" || undefined}
                                            onChange={(new_id) => changeItem(idx, new_id)}
                                            onDelete={() => removeItem(idx)}
                                            onMoveUp={idx == 0 ? undefined : () => moveItem(idx, "up")}
                                            onMoveDown={idx == editItem?.fields.length - 1 ? undefined : () => moveItem(idx, "down")}
                                            action={!!field && "edit" || undefined}
                                            onAction={() => {
                                                setEditFieldMode("edit");
                                                setEditField(!!field ? {...field} : null)
                                            }}
                                        />
                                    )
                                }
                            )
                        }
                        <InputFormFieldItem
                            options={unusedInputFields}
                            key={"newItem" + newItemKey}
                            item_id={null}
                            onChange={ addItem}
                            action={"add"}
                            onAction={() => {
                                setEditFieldMode("create");
                                setEditField({...newInputField});
                            }}
                        />
                    </AccordionDetails>
                </Accordion>



            </EditorDialog>

            <InputFieldEditor editItem={editField} setEditItem={setEditField} onSaveItem={onConfirmEditField} />
        </>
    )
}