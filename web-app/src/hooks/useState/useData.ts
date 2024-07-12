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

import {create} from "zustand";
import {FoodEnum, FoodEnumItem, FoodImage} from "../../models/main.ts";
import {usePersistentState} from "./usePersistentState.ts";
import {StudyDataStatsPerUser} from "../../models/studydata.ts";
import {User} from "../../models/auth.ts";
import {InputEnum, InputField, InputForm} from "../../models/form.ts";
import {makeNewStudy, Study} from "../../models/study.ts";

export const NEW_ELEMENT_ID = "f568748e-c57e-412e-9ef9-443e035386da";

type DataState = {
    foodImages: FoodImage[];
    foodItems: FoodEnumItem[];
    foodEnums: FoodEnum[];
    inputFields: InputField[];
    inputForms: InputForm[];
    inputEnums: InputEnum[];
    studies: Study[];
    users: User[]; // List of Participants when logged in as Scientist
    dataStats: StudyDataStatsPerUser | undefined;
    isLoading: boolean;
    updateFetchedStudies(studies: Study[]): void;
    createNewStudy(title: string): Study;
    updateStudy(study: Study): void;
}

/**
 * Local stare mirror of the data on the server.
 */
export const useDataState = create<DataState>()(
    (set, get) => ({
        foodImages: [],
        foodItems: [],
        foodEnums: [],
        studies: [],
        users: [],
        inputFields: [],
        inputForms: [],
        inputEnums: [],
        dataStats: undefined,
        isLoading: false,
        updateFetchedStudies: (studies) => {
            let newStudy : Study | undefined | Study[] = 
                get().studies.find((item: Study) => item.id === NEW_ELEMENT_ID);
            
            newStudy = newStudy ? [newStudy] : [];
            
            set({studies: [...studies, ...newStudy]});
        },
        createNewStudy: (title) => {
            
            const newStudy = makeNewStudy(title);

            // If there is already a new unsaved study in the list of studies, remove it first.
            const studies = get().studies;

            const index = get().studies.findIndex((item: Study) => item.id === NEW_ELEMENT_ID);
            if (index !== -1) {
                studies.splice(index, 1);
            }

            set({studies: [...studies, newStudy]});
            
            usePersistentState.setState({currentStudyId: newStudy.id});

            return newStudy;
        },
        updateStudy(study: Study) {
            const studies = get().studies;
            const index = studies.findIndex((item: Study) => item.id === study.id);
            study.modified = true;
            
            if (index !== -1) {
                studies[index] = study;
                set({studies: [...studies]});
            }
        }

    }));
