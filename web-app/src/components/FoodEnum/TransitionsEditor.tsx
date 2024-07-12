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

import {FoodEnum, FoodEnumItem, FoodEnumTransition} from "../../models/main.ts";
import {FC, useEffect, useState} from "react";
import {TransitionField} from "./TransitionField.tsx";
import {Button} from "@mui/material";

type TransitionsEditorProps = {
    foodEnum: FoodEnum;
    setFoodEnum: (item: FoodEnum) => void;
}


export const TransitionsEditor: FC<TransitionsEditorProps> = ({foodEnum, setFoodEnum}) => {

    const transitions = foodEnum.transitions;
    const setTransitions = (transitions: FoodEnumTransition[]) => setFoodEnum({...foodEnum, transitions});

    const moveItem = (idx: number, direction: "up" | "down")  => {
            const newTransitions = [...transitions];
            const item = newTransitions.splice(idx, 1)[0];
            const targetIdx = direction === "up" ? idx-1 : idx+1;
            newTransitions.splice(targetIdx, 0, item);
            setTransitions(newTransitions);
    };
    

    return (
        <div className={"enum-transition-editor"}>
            {
                foodEnum.transitions.map((transition, index) => (
                    <TransitionField
                        key={index}
                        selectableItemIds={foodEnum.item_ids} transition={transition} index={index}
                        setTransition={(transition) => {
                            const newTransitions = [...transitions];
                            newTransitions[index] = transition;
                            setTransitions(newTransitions);
                        }}
                        onDelete={() => {
                            const newTransitions = transitions.filter((_, i) => i !== index);
                            setTransitions(newTransitions);
                        }}

                        onMoveUp={index > 0 ? () => {
                            moveItem(index, "up");
                        } : undefined
                        }

                        onMoveDown={index < transitions.length -1 ? () => {
                            moveItem(index, "down");
                        } : undefined
                        }

                    />
                ))
            }
            <Button onClick={() => setTransitions([...transitions, {add_tags: [], require_tags: []}])}>Add transition...</Button>
        </div>
    )
}