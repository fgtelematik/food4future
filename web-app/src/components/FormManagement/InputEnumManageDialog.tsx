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

import {ObjectId} from "../../models/main.ts";
import {IdentifierCheckResult, InputEnum, newInputEnum} from "../../models/form.ts";
import {useDataState} from "../../hooks/useState/useData.ts";
import {useEffect, useState} from "react";
import useApi from "../../hooks/useApi";
import {useSessionState} from "../../hooks/useState/useSessionState.ts";
import {EditorDialog} from "../Editor/EditorDialog.tsx";
import {Autocomplete, Button, Card, createFilterOptions, Divider, IconButton, TextField} from "@mui/material";
import {ServerValidatedTextField} from "../Editor/ServerValidatedTextField.tsx";
import DeleteIcon from "@mui/icons-material/Delete";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";

const filterOptions = createFilterOptions<InputEnum>({
    stringify: (option) => option.identifier,
});


export const InputEnumManageDialog = (props: {
    enumId: ObjectId | null
    onChange: (newEnumId: ObjectId | null) => void,
    showEditor: boolean,
    setShowEditor: (show: boolean) => void,
    disabled?: boolean,
}) => {
    const allEnums = [newInputEnum, ...useDataState(state => state.inputEnums)];

    const [currentEnumId, setCurrentEnumId] = useState<ObjectId | null>(props.enumId);
    let selectedEnumSearchResult = currentEnumId == null ? null : allEnums.find(fi => fi.id === currentEnumId);
    const selectedEnum = selectedEnumSearchResult || newInputEnum;

    const [identifierValidationResult, setIdentifierValidationResult] = useState<IdentifierCheckResult | undefined>(IdentifierCheckResult.OK);
    const [currentEditEnum, setCurrentEditEnum] = useState<InputEnum>({...selectedEnum});
    const [isModified, setIsModified] = useState<boolean>(false);
    const [isSaving, setIsSaving] = useState<boolean>(false);

    const isValidIdentifier =
        !!currentEditEnum.identifier && (
            identifierValidationResult == IdentifierCheckResult.OK ||
            identifierValidationResult == IdentifierCheckResult.AlreadyInUse && currentEditEnum.identifier == selectedEnum.identifier)

    const showIdentifierError =
        !!identifierValidationResult && !!currentEditEnum.identifier && !isValidIdentifier;


    const isValidInput =
        currentEditEnum.identifier != "" &&
        isValidIdentifier &&
        currentEditEnum.items.length > 1 &&
        !currentEditEnum.items.some(it => it.identifier == "" || it.label == "");

    useEffect(() => {
        setCurrentEditEnum({...selectedEnum});
        setIsModified(false);
        setIdentifierValidationResult(IdentifierCheckResult.OK);
    }, [selectedEnum, props.showEditor]);

    useEffect(() => {
        setCurrentEnumId(props.enumId);
    }, [props.enumId, props.showEditor]);

    const onSaveCurrentEditEnum = async () => {
        setIsSaving(true);
        await useApi().upsertInputEnum(currentEditEnum);
        const enumId = currentEditEnum.id
        setCurrentEnumId(enumId); // id was updated by api call
        setIsSaving(false);
        return enumId;
    }

    const onDeleteEnum = async () => {
        if(!selectedEnum || selectedEnum == newInputEnum)
            return;

        useSessionState.setState({
            alertDialog: {
                title: "Delete Selection",
                content: "Do you really want to delete this selection?",
                confirmText: "Delete",
                cancelText: "Cancel",
                onConfirm: async () => {
                    await useApi().deleteInputEnum(selectedEnum);
                    setCurrentEnumId(null);

                    if(props.enumId == selectedEnum.id)
                        props.onChange(null);
                }
            }
        })
    }

    const onAddEnumItem = () => {
        setCurrentEditEnum({
            ...currentEditEnum,
            items: [...currentEditEnum.items, {identifier: "", label: ""}]
        });
        setIsModified(true);
    }

    const onSubmit = async () => {
        let enumId = currentEnumId;

        if(isModified) {
            if(!isValidInput)
                throw Error("Tried to submit input enum with invalid input values");
            enumId = await onSaveCurrentEditEnum();
        }
        props.onChange(enumId);
        props.setShowEditor(false);
    }

    const onChangeEnum = (newEnum: InputEnum) => {
        const doChangeEnum = () => {
            setCurrentEnumId(newEnum.id);
        }

        if(isModified) {
            useSessionState.setState({
                alertDialog: {
                    title: "Unsaved Changes",
                    content: <>You have unsaved changes for {
                        currentEditEnum.identifier ? <>the selection '<b>{currentEditEnum.identifier}</b>'</> : "this selection"
                    }.<br/>Do you want to discard them?</>,
                    confirmText: "Discard",
                    cancelText: "Cancel",
                    onConfirm: () => {
                        doChangeEnum();
                    }
                }
            })
        } else {
            doChangeEnum();
        }
    }

    return (
        <EditorDialog
            open={props.showEditor}
            onSubmit={onSubmit}
            onCancel={() => props.setShowEditor(false)}
            isValidContent={isValidInput}

        >
            <div className={"field-row stretch-first"}>
            <Autocomplete
                disabled={props.disabled}
                filterOptions={filterOptions}
                options={allEnums}
                disableClearable={true}
                getOptionLabel={(item) => item == newInputEnum ? "Create new..." : item.identifier}
                value={selectedEnum}
                onChange={(_, value) => onChangeEnum(value)}
                isOptionEqualToValue={(option, value) => !value ? false : option.id === value.id}
                renderInput={(params) => (<>
                        <TextField
                            {...params}
                            label={"Chosen Selection"}
                            style={selectedEnum == newInputEnum ? {fontStyle: "italic"} : {}}
                        /></>
                )}
            />
                <IconButton disabled={selectedEnum == newInputEnum} onClick={onDeleteEnum}><DeleteIcon
                    fontSize={"small"}/></IconButton>
            </div>
            <Divider/>
            <ServerValidatedTextField
                validateFn={useApi().checkInputEnumIdentifier}
                updateValidationResult={setIdentifierValidationResult}
                isPositiveValidationResult={!!currentEditEnum.identifier  && isValidIdentifier}
                label={"Selection Identifier"}
                placeholder={"MySelection"}
                value={currentEditEnum.identifier}
                error={!currentEditEnum.identifier || showIdentifierError}
                helperText={!currentEditEnum.identifier ? "Identifier required." :
                (showIdentifierError ? "Selection identifier is invalid or already exists" : "")}
                onChange={(e) => {
                    setCurrentEditEnum({...currentEditEnum, identifier: e.target.value});
                    setIsModified(true);
                }}
            />
            <Card className={"enum-items-container"}>
                <h2>Selectable Items</h2>
                <div>
                    {
                        currentEditEnum.items.map((item, idx) => {
                            const isLast = idx == currentEditEnum.items.length - 1;
                            const istFirst = idx == 0;
                            const isOnly = istFirst && isLast;

                            const onDelete = !isOnly ? () => {
                                setCurrentEditEnum({
                                    ...currentEditEnum,
                                    items: currentEditEnum.items.filter((_, i) => i != idx)
                                });
                                setIsModified(true);
                            } : undefined;

                            const onMoveUp = !istFirst ? () => {
                                setCurrentEditEnum({
                                    ...currentEditEnum,
                                    items: currentEditEnum.items.map((it, i) => i == idx - 1 ? currentEditEnum.items[idx] : i == idx ? currentEditEnum.items[idx - 1] : it)
                                });
                                setIsModified(true);
                            } : undefined;

                            const onMoveDown = !isLast ? () => {
                                setCurrentEditEnum({
                                    ...currentEditEnum,
                                    items: currentEditEnum.items.map((it, i) => i == idx ? currentEditEnum.items[idx + 1] : i == idx + 1 ? currentEditEnum.items[idx] : it)
                                });
                                setIsModified(true);
                            } : undefined;

                            const num = idx + 1;


                            return <div className={"enum-item-row"} key={currentEditEnum.id + "-" + idx}>
                                <p><b>{num}.</b></p>
                                <TextField
                                    label={"Item Identifier"}
                                    onFocus={(e) => e.target.select()}
                                    InputLabelProps={{shrink: true}}
                                    value={item.identifier}
                                    error={item.identifier == ""}
                                    placeholder={"item" + num}
                                    helperText={item.identifier == "" ? "Identifier required." : undefined}
                                    onChange={(e) => {
                                        setCurrentEditEnum({
                                            ...currentEditEnum,
                                            items: currentEditEnum.items.map((it, i) => i == idx ? {...it, identifier: e.target.value} : it)
                                        });
                                        setIsModified(true);
                                    }}
                                />
                                <TextField
                                    label={"Label"}
                                    onFocus={(e) => e.target.select()}
                                    InputLabelProps={{shrink: true}}
                                    error={item.label == ""}
                                    placeholder={"Item " + num}
                                    helperText={item.label == "" ? "Label required." : undefined}
                                    value={item.label}
                                    onChange={(e) => {
                                        setCurrentEditEnum({
                                            ...currentEditEnum,
                                            items: currentEditEnum.items.map((it, i) => i == idx ? {...it, label: e.target.value} : it)
                                        });
                                        setIsModified(true);
                                    }}
                                />
                                <IconButton disabled={!onDelete} onClick={onDelete}><DeleteIcon
                                    fontSize={"small"}/></IconButton>
                                <IconButton disabled={!onMoveUp} onClick={ onMoveUp}><ArrowUpwardIcon
                                    fontSize={"small"}/></IconButton>
                                <IconButton disabled={!onMoveDown} onClick={onMoveDown}><ArrowDownwardIcon
                                    fontSize={"small"}/></IconButton>
                            </div>
                        })
                    }

                </div>

                {currentEditEnum.items.length < 2 && <p className={"error"}>A minimum of two selection items is required.</p>}
                <Button onClick={onAddEnumItem}>Add item...</Button>

            </Card>



        </EditorDialog>
    )

}