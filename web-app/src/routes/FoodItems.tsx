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

import {useEffect, useState} from "react";
import {FoodEnumItem} from "../models/main.ts";
import {useDataState} from "../hooks/useState/useData.ts";
import useApi from "../hooks/useApi";
import {useSessionState} from "../hooks/useState/useSessionState.ts";
import {locStr} from "../utils.ts";
import {Button, IconButton, InputAdornment, TextField} from "@mui/material";
import SearchIcon from "@mui/icons-material/Search";
import ClearIcon from "@mui/icons-material/Clear";
import {FoodItemEditor} from "../components/FoodItem/FoodItemEditor.tsx";
import {FoodItemEntry} from "../components/FoodItem/FoodItemEntry.tsx";

export const FoodItemsView = () => {
    const [currentEditItem, setCurrentEditItem] = useState<FoodEnumItem | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [filterValue, setFilterValue] = useState<string>("");

    const items = useDataState(state => state.foodItems);

    let visibleItems = !filterValue ? items : items.filter(
        item => 
            item.label && locStr(item.label).toLowerCase().includes(filterValue.toLowerCase())
            ||
            item.identifier?.toLowerCase().includes(filterValue.toLowerCase())
    )

    useEffect(() => {
        const fetchItems = async () => {
            setLoading(true);
            try {
                await useApi().fetchFoodItems();
            } catch (e) {
                console.error(e);
            }
            setLoading(false);
        }

        fetchItems();
    }, [])

    const onSaveItem = async (item: FoodEnumItem) => {
        try {
            await useApi().upsertFoodItem(item);
        } catch (e) {
            return false;
        }
        return true;
    }

    const onDelete = async (item: FoodEnumItem) => {
        useSessionState.setState({
            alertDialog: {
                content: <>Delete Food Item <br/><b>{locStr(item.label)}</b> ?</>,
                onConfirm: async () => {
                    try {
                        await useApi().deleteFoodItem(item);
                    } catch (e) {
                        console.error(e);
                    }
                },
                onCancel: () => {
                },
                confirmText: "Yes",
                cancelText: "No"
            }
        });
    }

    return (

        <div className={"items-list-container"}>
            <div className={"items-list-actions"}>

                <TextField
                    value={filterValue || ""}

                    onChange={e => {
                        setFilterValue(String(e.target.value));
                    }}

                    InputProps={{
                        startAdornment: (
                            <InputAdornment position="start">
                                <SearchIcon/>
                            </InputAdornment>
                        ),
                        endAdornment: (
                            <IconButton sx={{visibility: filterValue ? "visible" : "hidden"}} onClick={() => {
                                setFilterValue("")
                            }}><ClearIcon/></IconButton>)
                    }}

                    variant="standard"
                    placeholder={`Search food items ...`}
                    style={{
                        fontSize: '1.1rem',
                        border: '0',
                    }}
                />
                <Button onClick={() => setCurrentEditItem({is_food_item: true})}>
                    Add Food Item
                </Button>
            </div>

            <div className={"items-list"}>
                {visibleItems.map(item => <FoodItemEntry key={item.id} item={item} onEdit={() => {
                    setCurrentEditItem(item)
                }} onDelete={() => onDelete(item)
                }/>)}
            </div>

            <FoodItemEditor editItem={currentEditItem} setEditItem={setCurrentEditItem} onSaveItem={onSaveItem}/>
        </div>
    )
}