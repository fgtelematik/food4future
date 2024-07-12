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
import {FoodImage} from "../models/main.ts";
import '../style/FoodImageView.css'
import {FoodImageEditor} from "../components/FoodImage/FoodImageEditor.tsx";
import {FoodImageEntry} from "../components/FoodImage/FoodImageEntry.tsx";
import useApi from "../hooks/useApi";
import {useDataState} from "../hooks/useState/useData.ts";
import {useSessionState} from "../hooks/useState/useSessionState.ts";
import {Button, IconButton, InputAdornment, TextField} from "@mui/material";
import SearchIcon from '@mui/icons-material/Search.js';
import ClearIcon from '@mui/icons-material/Clear';
import {locStr} from "../utils.ts";

export const FoodImagesView = () => {
    const [currentEditItem, setCurrentEditItem] = useState<FoodImage | null>(null);
    const [loading, setLoading] = useState<boolean>(false);
    const [filterValue, setFilterValue] = useState<string>("");


    const images = useDataState(state => state.foodImages);

    let visibleImages = !filterValue ? images : images.filter(image => 
        image.label && locStr(image.label).toLowerCase().includes(filterValue.toLowerCase())
    )

    useEffect(() => {
        const fetchImages = async () => {
            setLoading(true);
            try {
                await useApi().fetchImages();
            } catch (e) {
                console.error(e);
            }
            setLoading(false);
        }

        fetchImages();
    }, [])

    const onSaveItem = async (image: FoodImage) => {
        try {
            await useApi().upsertImage(image);
        } catch (e) {
            return false;
        }
        return true;
    }

    const onDelete = async (image: FoodImage) => {
        useSessionState.setState({
            alertDialog: {
                content: <>Delete Image <br/><b>{locStr(image.label)}</b> ?</>,
                onConfirm: async () => {
                    try {
                        await useApi().deleteImage(image);
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
                    placeholder={`Search images ...`}
                    style={{
                        fontSize: '1.1rem',
                        border: '0',
                    }}
                />
                <Button onClick={() => setCurrentEditItem({})}>
                    Add Image
                </Button>
            </div>

            <div className={"items-list"}>
                {visibleImages.map(image => <FoodImageEntry key={image.id} item={image} onEdit={() => {
                    setCurrentEditItem(image)
                }} onDelete={() => onDelete(image)
                }/>)}
            </div>

            <FoodImageEditor editItem={currentEditItem} setEditItem={setCurrentEditItem} onSaveItem={onSaveItem}/>
        </div>
    )
}