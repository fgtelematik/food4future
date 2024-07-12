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
    fieldTypeLabels,
    IdentifierCheckResult,
    identifierHelperText,
    InputField,
    InputForm
} from "../../models/form.ts";
import {NEW_ELEMENT_ID, useDataState} from "../../hooks/useState/useData.ts";
import {ServerValidatedTextField} from "../Editor/ServerValidatedTextField.tsx";
import useApi from "../../hooks/useApi";
import {Autocomplete, FormControlLabel, FormGroup, Switch, TextField} from "@mui/material";
import {InputEnumField} from "./InputEnumField.tsx";
import {InputDefaultValueField} from "./InputDefaultValueField.tsx";
import {InputPermissionsField} from "./InputPermissionsField.tsx";
import {InputPeriodicDisplayField} from "./InputPeriodicDisplayField.tsx";


export const InputFieldEditor: FC<EditorProps<InputField>> = ({editItem, setEditItem, onSaveItem}) => {
    const isNew = !editItem || editItem?.id == NEW_ELEMENT_ID;

    const [identifierValidationResult, setIdentifierValidationResult] = useState<IdentifierCheckResult | undefined>(IdentifierCheckResult.OK);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const [originalIdentifier, setOriginalIdentifier] = useState<string | undefined>(undefined);

    const otherForms = useDataState(state => state.inputForms)
        .filter(form => form.id !== editItem?.id);

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

        // Set defaults for optional fields:
        if (!editItem.helpText)
            editItem.helpText = "";

        if (!editItem.elements_type)
            editItem.elements_type = FieldType.StringType;

        if (!editItem.elements_type)
            editItem.elements_type = FieldType.StringType;


    }, [editItem?.id]);

    const isValidIdentifier =
        identifierValidationResult == IdentifierCheckResult.OK ||
        identifierValidationResult == IdentifierCheckResult.AlreadyInUse && editItem?.identifier == originalIdentifier

    const isNumericType = !!editItem && (
        editItem.datatype == FieldType.IntType || editItem.datatype == FieldType.FloatType ||
        editItem.datatype == FieldType.ListType &&
        (editItem.elements_type == FieldType.IntType || editItem.elements_type == FieldType.FloatType)
    );

    const isStringType = !!editItem && (
        editItem.datatype == FieldType.StringType ||
        editItem.datatype == FieldType.ListType &&
        (editItem.elements_type == FieldType.StringType)
    );

    const isEnumType = !!editItem && (
        editItem.datatype == FieldType.EnumType ||
        editItem.datatype == FieldType.ListType &&
        (editItem.elements_type == FieldType.EnumType)
    );

    const isSubformType = !!editItem && (
        editItem.datatype == FieldType.FormType ||
        editItem.datatype == FieldType.ListType &&
        (editItem.elements_type == FieldType.FormType)
    );

    const referencedSubform:InputForm | null  =
        isSubformType &&  !!editItem?.adt_enum_id &&
        otherForms.find(form => form.id === editItem.adt_enum_id)
        || null;


    const isMissingReferencedSubform =
        isSubformType && !!editItem?.adt_enum_id && !referencedSubform;

    const isNonOptionalBooleanField = !!editItem && editItem.datatype == FieldType.BoolType && !editItem.maybeNull;

    const isValidInput = isValidIdentifier && !!editItem?.label; // TODO: Add conditions
    const onConfirm = async () => {
        if (!editItem)
            return;

        const success = await onSaveItem(editItem);
        if (success)
            setEditItem(null);
    }


    return (editItem &&
        <>
            <EditorDialog
                open={true}
                title={(isNew ? "Create" : "Edit") + " Form Field"}
                onSubmit={() => onConfirm()}
                onCancel={() => setEditItem(null)}
                isValidContent={isValidInput}
            >
                <div className={"field-row  stretch-first"}>

                    <ServerValidatedTextField
                        validateFn={useApi().checkInputFieldIdentifier}
                        updateValidationResult={setIdentifierValidationResult}
                        isPositiveValidationResult={!!editItem?.identifier && identifierValidationResult == IdentifierCheckResult.OK}
                        disabled={isSubmitting}
                        value={editItem?.identifier ?? ""}
                        onChange={(e) => setEditItem({...editItem!, identifier: e.target.value})}
                        label={"Field Identifier*"}
                        error={!!identifierValidationResult && !isValidIdentifier}
                        helperText={isValidIdentifier ? "" : (identifierValidationResult && identifierHelperText[identifierValidationResult])}
                    />
                    <FormGroup>
                        <FormControlLabel control={<Switch
                            checked={editItem.maybeNull}
                            onChange={(e) => setEditItem({...editItem!, maybeNull: e.target.checked})}
                        />} label="Optional"/>
                    </FormGroup>
                </div>


                <TextField
                    label={"Label*"}
                    value={editItem?.label ?? ""}
                    onChange={(e) => setEditItem({...editItem!, label: e.target.value})}
                    helperText={"The label of the field displayed to the user" }
                />

                <TextField
                    label={"Helper Text"}
                    value={editItem?.helpText ?? ""}
                    onChange={(e) => setEditItem({...editItem!, helpText: e.target.value})}
                    helperText={"An optional instructive text displayed to the user."}
                />

                <div className={"field-row stretch-first"}>
                    <Autocomplete
                        disabled={isSubmitting}
                        freeSolo={false}
                        disableClearable
                        options={Object.values(FieldType)}
                        getOptionLabel={(option) => fieldTypeLabels[option as FieldType]}
                        value={editItem.datatype}
                        onChange={(_, value) => setEditItem({...editItem!, datatype: value as FieldType})}
                        renderInput={(params) => (
                            <TextField
                                {...params}
                                label={"Field Type"}
                                helperText={isNonOptionalBooleanField ? <><b>Attention:</b> You should set this field <b>optional</b>, if you do not explicitly wish to require the user to tick the checkbox (e.g. for accepting agreements).</> : undefined}
                                InputProps={{
                                    ...params.InputProps,
                                }}
                            />
                        )}
                    />

                    {isNumericType &&
                        <FormGroup>
                            <FormControlLabel control={<Switch
                                checked={editItem.useSlider}
                                onChange={(e) => setEditItem({...editItem!, useSlider: e.target.checked})}
                            />} label="Display as Rating Scale"/>
                        </FormGroup>}
                </div>


                {editItem?.datatype == FieldType.ListType &&
                    <Autocomplete
                        disabled={isSubmitting}
                        freeSolo={false}
                        disableClearable
                        options={Object.values(FieldType).filter(type => type != FieldType.ListType && type != FieldType.BoolType)}
                        getOptionLabel={(option) => fieldTypeLabels[option as FieldType]}
                        value={editItem.elements_type}
                        onChange={(_, value) => setEditItem({...editItem!, elements_type: value as FieldType})}
                        renderInput={(params) => (
                            <TextField
                                {...params}
                                label={"List Element Type"}
                                InputProps={{
                                    ...params.InputProps,
                                }}
                            />
                        )}
                    />
                }

                {isEnumType &&
                    <InputEnumField
                        enumId={editItem?.adt_enum_id ?? null}
                        onChange={(newEnumId) => setEditItem({...editItem!, adt_enum_id: newEnumId ?? undefined})}
                        label={"Selection"}
                        disabled={isSubmitting}
                        error={!editItem?.adt_enum_id}
                        helperText={!!editItem?.adt_enum_id ? "" : <>Please select a selection.<br/>Click <i>Manage Selections</i> to create a new selection set.</>}
                        />
                }

                {isSubformType &&
                    <Autocomplete
                        disabled={isSubmitting}
                        freeSolo={false}
                        options={otherForms}
                        getOptionLabel={(option) => option.identifier}
                        value={referencedSubform}
                        noOptionsText={"No other forms available"}
                        onChange={(_, value) => setEditItem({...editItem!, adt_enum_id: value?.id ?? undefined})}
                        renderInput={(params) => (
                            <TextField
                                {...params}
                                label={editItem?.datatype == FieldType.ListType ? "Element Form" : "Target Form"}
                                error={isMissingReferencedSubform || !editItem?.adt_enum_id}
                                helperText={isMissingReferencedSubform ? "The referenced subform does not exist." : (
                                    !editItem?.adt_enum_id ? "Please select a subform." : ""
                                )}
                                placeholder={isMissingReferencedSubform ? "(missing ID: "+ editItem?.adt_enum_id+")" : undefined}
                                InputProps={{
                                    ...params.InputProps,
                                }}
                            />
                        )}
                    />
                }

                <InputDefaultValueField
                    maybeNull={!!editItem.maybeNull}
                    datatype={editItem.datatype}
                    value={editItem.defaultValue}
                    setValue={(value) => setEditItem({...editItem!, defaultValue: value})}
                    enum_id={editItem.adt_enum_id}
                />



                {isNumericType && !editItem.useSlider &&
                    <TextField
                        label={"Unit (optional)"}
                        placeholder={"ml, kg, ..."}
                        value={editItem?.unitString ?? ""}
                        onChange={(e) => setEditItem({...editItem!, unitString: e.target.value})}
                        helperText={"An optional unit string displayed behind the input value"}
                    />}

                {isNumericType &&
                    <div className={"field-row"}>
                        <TextField
                            type={"number"}
                            label={"Minimum value" + (editItem.useSlider ? "" : " (optional)")}
                            value={editItem?.minValue ?? ""}
                            onChange={(e) => setEditItem({
                                ...editItem!,
                                minValue: !e.target.value ? undefined : parseFloat(e.target.value)
                            })}
                        />

                        <TextField
                            type={"number"}
                            label={"Maximum value" + (editItem.useSlider ? "" : " (optional)")}
                            value={editItem?.maxValue ?? ""}
                            onChange={(e) => setEditItem({
                                ...editItem!,
                                maxValue: !e.target.value ? undefined : parseFloat(e.target.value)
                            })}
                        />
                    </div>
                }

                {isNumericType && editItem.useSlider &&
                    <div className={"field-row"}>
                        <TextField
                            label={"Option Minimum Label"}
                            value={editItem?.sliderMinLabel ?? ""}
                            onChange={(e) => setEditItem({...editItem!, sliderMinLabel: e.target.value})}
                            helperText={"Label displayed at the very left option."}
                        />

                        <TextField
                            label={"Option Maximum Label"}
                            value={editItem?.sliderMaxLabel ?? ""}
                            onChange={(e) => setEditItem({...editItem!, sliderMaxLabel: e.target.value})}
                            helperText={"Label displayed at the very right option."}
                        />
                    </div>}

                {isNumericType && editItem.useSlider &&
                    <TextField
                        type={"number"}
                        label={"Slider Step Size"}
                        placeholder={"e.g. 1 or 0.1 ..."}
                        value={editItem?.sliderStepSize ?? ""}
                        onChange={(e) => setEditItem({
                            ...editItem!,
                            sliderStepSize: !e.target.value ? undefined : parseFloat(e.target.value)
                        })}
                    />}


                {((isNumericType && !editItem.useSlider) || isStringType) &&
                    <FormGroup>
                        <FormControlLabel control={<Switch
                            checked={editItem.qrCodeInput}
                            onChange={(e) => setEditItem({...editItem!, qrCodeInput: e.target.checked})}
                        />} label={<>Add <i>Read from QR/Barcode</i> Button</>}/>
                    </FormGroup>}

                <InputPermissionsField permissions={editItem.permissions} setPermissions={(newPermissions) => {
                    setEditItem({...editItem!, permissions: newPermissions})
                }} />

                <InputPeriodicDisplayField field={editItem} updateField={(newField) => setEditItem(newField)}/>

            </EditorDialog>
        </>
    )
}