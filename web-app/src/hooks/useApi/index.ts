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

import {usePersistentState} from "../useState/usePersistentState.ts";
import {useSessionState} from "../useState/useSessionState.ts";
import {downloadFileFromPostRequest, fetchEx, getEndpointUrl, uploadFile} from "./utils.ts";
import {FoodEnum, FoodEnumItem, FoodImage, ObjectId} from "../../models/main.ts";
import {NEW_ELEMENT_ID, useDataState} from "../useState/useData.ts";
import {PartialBy} from "../../utils.ts";
import {ResendMailRequest, TestUsernameResult, User, UserInput} from "../../models/auth.ts";
import {StudyDataRequest, StudyDataStatsPerUser} from "../../models/studydata.ts";
import {IdentifierCheckResult, InputEnum, InputField, InputForm} from "../../models/form.ts";
import {Study} from "../../models/study.ts";

// TODO: There is a lot of redundant code in this file. Might be refactored in the future.

export enum Endpoint {
    token = 'token',
    logout = 'logout',
    me = 'me',
    foodimage = 'schema/foodimage',
    foodimage_list = 'schema/foodimages',
    fooditem = 'schema/foodenumitem',
    fooditem_list = 'schema/foodenumitems',
    foodenum = 'schema/foodenum',
    foodenum_list = 'schema/foodenums',
    study_list = 'schema/studies',
    study = 'schema/study',
    staff = 'staff',
    generate_username = 'user/generate_username',
    test_username = 'user/test_username',
    user = 'user',
    image = 'images', // legacy path, todo: specific image endpoint
    resend_mail = 'sendmail',
    data_stats = 'data/v2/stats',
    data_request_csv = 'data/v2/request_csv',
    users = 'users',
    input_forms_list = 'forms',
    input_form = 'forms/form',
    input_form_check_identifier = 'forms/check_identifier',
    input_field = 'forms/field',
    input_fields_list = 'forms/fields',
    input_field_check_identifier = 'forms/field/check_identifier',
    input_enum = 'forms/enum',
    input_enums_list = 'forms/enums',
    input_enum_check_identifier = 'forms/enum/check_identifier',


    // ...
}

export const API_URL_PRODUCTION = "https://f4f.tm.th-wildau.de/"
export const API_URL_LOCAL = "http://localhost:8000/"
export const API_URL_TEST = "https://f4f.tm.th-wildau.de:8443/"

const extended_timeout = 5 * 60 * 1000;

const useApi = () => {
    return ({
        login: async (username: string, password: string): Promise<void> => {
            if (usePersistentState.getState().apiToken !== null)
                await useApi().logout();

            // Obtain JWT according to OAuth 2.0
            const params = new URLSearchParams();
            params.append("username", username);
            params.append("password", password);

            const resp = await fetchEx(getEndpointUrl(Endpoint.token), "POST", params.toString(), {
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
                }
            }, false, true );

            const json = await resp.json();
            usePersistentState.setState({apiToken: json.access_token});
            await useApi().fetchCurrentUser();

            // Reset user-specific cached data
            useDataState.setState({
                dataStats : undefined,
                users: [],
            });
        },

        logout: async () => {
            fetchEx(getEndpointUrl(Endpoint.logout), "GET").catch(e => console.log(e));
            usePersistentState.setState({apiToken: null});
            useSessionState.setState({user: null});

        },

        fetchCurrentUser: async () => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.me));
            const json = await resp.json();
            useSessionState.setState({user: json});
        },
        getImageUrl: (imageFilename: string) => {
            return getEndpointUrl(Endpoint.image) + "/" + imageFilename;
        },
        
        upsertImage: async (image: FoodImage) => {
            const uploadRequired = image.filename instanceof File;
            let imageJson: FoodImage;
            if(uploadRequired)
                imageJson = await uploadFile<FoodImage>(getEndpointUrl(Endpoint.foodimage), "image_file", image.filename as File, {food_image_json: image});
            else {
                const response = await fetchEx(getEndpointUrl(Endpoint.foodimage), "PUT", JSON.stringify(image));
                imageJson = await response.json()
            }
            await useApi().fetchImages();
            
            const newImage = useDataState.getState().foodImages.find(fi => fi.id == imageJson.id);
            
            return newImage ?? imageJson;
        },
        
        fetchImages: async () => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.foodimage_list));
            const json = await resp.json();
            useDataState.setState({foodImages : json});
        },
        deleteImage: async (image: FoodImage) => {
            await fetchEx(getEndpointUrl(Endpoint.foodimage) + "/" + image.id, "DELETE");
            await useApi().fetchImages();
        },
        
        fetchFoodItems: async () => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.fooditem_list));
            const foodItems = await resp.json();
            await useApi().fetchImages();
            
            for(const foodItemObj of foodItems) {
                if(foodItemObj["image"]) {
                    const foodImage = useDataState.getState().foodImages.find((image: FoodImage) => image.id === foodItemObj["image"]);
                    if(foodImage)
                        foodItemObj["image"] = foodImage;
                    else {
                        foodItemObj["image"] = undefined;
                        console.warn("Food item " + foodItemObj["id"] + " has an image that does not exist in the database.");
                    }
                }
            }
            
            useDataState.setState({foodItems : foodItems as FoodEnumItem[]});
        },
        
        upsertFoodItem: async (foodItem: FoodEnumItem) => {
            const imageId = foodItem.image?.id ?? null;
            const isNew = !foodItem.id;
            let foodItemJson = {...foodItem, image: imageId};
            const resp = await fetchEx(getEndpointUrl(Endpoint.fooditem), "PUT", JSON.stringify(foodItemJson));
            foodItemJson = await resp.json();
            foodItem.id = foodItemJson.id;
            
            const foodItems = useDataState.getState().foodItems;
            
            if(isNew) {
                foodItems.push(foodItem);
                useDataState.setState({foodItems: [...foodItems]});
            } else {
                const index = foodItems.findIndex((item: FoodEnumItem) => item.id === foodItem.id);
                if(index !== -1) {
                    foodItems[index] = foodItem;
                    useDataState.setState({foodItems: [...foodItems]});
                }
            }
        },
        
        deleteFoodItem: async (foodItem: FoodEnumItem) => {
            await fetchEx(getEndpointUrl(Endpoint.fooditem) + "/" + foodItem.id, "DELETE");
            
            const foodItems = useDataState.getState().foodItems;
            const index = foodItems.findIndex((item: FoodEnumItem) => item.id === foodItem.id);
            if(index !== -1) {
                foodItems.splice(index, 1);
                useDataState.setState({foodItems: [...foodItems]});
            }
        },
        
        fetchFoodEnums: async () => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.foodenum_list));
            const foodEnums = await resp.json();
            await useApi().fetchFoodItems();
            useDataState.setState({foodEnums: foodEnums});
        },
        
        upsertFoodEnum: async (foodEnum: FoodEnum) => {
            const isNew = !foodEnum.id;
            const resp = await fetchEx(getEndpointUrl(Endpoint.foodenum), "PUT", JSON.stringify(foodEnum));
            const foodEnumJson = await resp.json();
            foodEnum.id = foodEnumJson.id;
            
            const foodEnums = useDataState.getState().foodEnums;
            
            if(isNew) {
                foodEnums.push(foodEnum);
                useDataState.setState({foodEnums: [...foodEnums]});
            } else {
                const index = foodEnums.findIndex((item: FoodEnum) => item.id === foodEnum.id);
                if(index !== -1) {
                    foodEnums[index] = foodEnum;
                    useDataState.setState({foodEnums: [...foodEnums]});
                }
            }
                        
        },
        
        deleteFoodEnum: async (foodEnum: FoodEnum) => {
            await fetchEx(getEndpointUrl(Endpoint.foodenum) + "/" + foodEnum.id, "DELETE");
            
            const foodEnums = useDataState.getState().foodEnums;
            const index = foodEnums.findIndex((item: any) => item.id === foodEnum.id);
            if(index !== -1) {
                foodEnums.splice(index, 1);
                useDataState.setState({foodEnums: [...foodEnums]});
            }
        },
        
        fetchStudies: async () => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.study_list));
            const studies = await resp.json();
            useDataState.getState().updateFetchedStudies(studies);
        },
        
        deleteStudy: async (studyId: ObjectId) => {
            if(studyId !== NEW_ELEMENT_ID)
                await fetchEx(getEndpointUrl(Endpoint.study) + "/" + studyId, "DELETE");
            
            const currentStudyId = usePersistentState.getState().currentStudyId;
            if(currentStudyId === studyId)
                usePersistentState.setState({currentStudyId: null});
            
            const studies = useDataState.getState().studies;
            const index = studies.findIndex((item: Study) => item.id === studyId);
            if(index !== -1) {
                studies.splice(index, 1);
                useDataState.setState({studies: [...studies]});
            }
        },
        
        upsertStudy: async (study: Study) => {
            const isNew = study.id === NEW_ELEMENT_ID;
            const studyObj : PartialBy<Study, "id"> = {...study};

            if (isNew) {
                delete studyObj.id;
            }
            delete studyObj.modified;

            const resp = await fetchEx(getEndpointUrl(Endpoint.study), "PUT", JSON.stringify(studyObj));

            const studies = useDataState.getState().studies;
            const index = studies.findIndex((item: Study) => item.id === study.id);

            if(index !== -1) {
                const upsertedStudy = await resp.json();
                studies[index] = upsertedStudy;
                useDataState.setState({studies: [...studies]});
                usePersistentState.setState({currentStudyId: upsertedStudy.id});
            }
        },
        
        fetchStaff: async (studyId: ObjectId): Promise<User[]> => {
            if(!studyId || studyId === NEW_ELEMENT_ID) {
                return [];
            }
            
            const resp = await fetchEx(getEndpointUrl(Endpoint.staff) + "/" + studyId);
            return await resp.json();
        },
        
        generateUsername: async () => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.generate_username));
            return await resp.json();
        },
        
        upsertUser: async (user: UserInput) => {
            await fetchEx(getEndpointUrl(Endpoint.user), !!user.id ? "PUT" : "POST", JSON.stringify(user));
        },
        
        testUsername:  async (username: string) => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.test_username) + "/" + username);
            return await resp.json() as TestUsernameResult;
        },
        
        deleteUser: async (userId: ObjectId) => {
            await fetchEx(getEndpointUrl(Endpoint.user) + "/" + userId, "DELETE");
        },
        
        resendRegistrationMail: async (userId: ObjectId, request: ResendMailRequest) => {
            await fetchEx(getEndpointUrl(Endpoint.resend_mail) + "/" + userId, "POST", JSON.stringify(request));
        },

        fetchStudyDataStats: async () => {
            const resp = await fetchEx(
                getEndpointUrl(Endpoint.data_stats),
                "GET",
                null,
                {},
                false,
                false,
                extended_timeout
            );
            const stats_data = await resp.json();
            useDataState.setState({dataStats : stats_data as StudyDataStatsPerUser});
        },

        fetchUsers: async () => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.users));
            const users = (await resp.json()).users as User[];
            useDataState.setState({users : users});
        },

        generateStudyDataCSV: async (request: StudyDataRequest) =>
            downloadFileFromPostRequest(getEndpointUrl(Endpoint.data_request_csv), request),

        fetchAllFormData: async () => {
            let resp = await fetchEx(getEndpointUrl(Endpoint.input_fields_list));
            const inputFields = await resp.json();
            useDataState.setState({inputFields : inputFields});

            resp = await fetchEx(getEndpointUrl(Endpoint.input_forms_list));
            const inputForms = await resp.json();
            useDataState.setState({inputForms : inputForms});

            resp = await fetchEx(getEndpointUrl(Endpoint.input_enums_list));
            const inputEnums = await resp.json();
            useDataState.setState({inputEnums : inputEnums});
        },

        checkInputFormIdentifier: async (identifier: string) => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.input_form_check_identifier) + "/" + identifier);
            return await resp.json() as IdentifierCheckResult;
        },

        checkInputFieldIdentifier: async (identifier: string) => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.input_field_check_identifier) + "/" + identifier);
            return await resp.json() as IdentifierCheckResult;
        },

        checkInputEnumIdentifier: async (identifier: string) => {
            const resp = await fetchEx(getEndpointUrl(Endpoint.input_enum_check_identifier) + "/" + identifier);
            return await resp.json() as IdentifierCheckResult;
        },

        upsertInputField: async (inputField: InputField) => {
            const isNew = inputField.id == NEW_ELEMENT_ID;
            const inputFieldObj : Partial<InputField> = {...inputField};

            if (isNew) {
                delete inputFieldObj.id;
            }

            const resp = await fetchEx(getEndpointUrl(Endpoint.input_field), "PUT", JSON.stringify(inputFieldObj) );
            const inputFieldJson = await resp.json();
            inputField.id = inputFieldJson.id;

            const inputFields = useDataState.getState().inputFields;

            if(isNew) {
                inputFields.push(inputField);
                useDataState.setState({inputFields: [...inputFields]});
            } else {
                const index = inputFields.findIndex((item: InputField) => item.id === inputField.id);
                if(index !== -1) {
                    inputFields[index] = inputField;
                    useDataState.setState({inputFields: [...inputFields]});
                }
            }
        },

        upsertInputForm: async (inputForm: InputForm) => {
            const isNew = inputForm.id == NEW_ELEMENT_ID;
            const inputFormObj: Partial<InputForm> = {...inputForm};

            if (isNew) {
                delete inputFormObj.id;
            }

            const resp = await fetchEx(getEndpointUrl(Endpoint.input_form), "PUT", JSON.stringify(inputFormObj));
            const inputFormJson = await resp.json();
            inputForm.id = inputFormJson.id;

            const inputForms = useDataState.getState().inputForms;

            if (isNew) {
                inputForms.push(inputForm);
                useDataState.setState({inputForms: [...inputForms]});
            } else {
                const index = inputForms.findIndex((item: InputForm) => item.id === inputForm.id);
                if (index !== -1) {
                    inputForms[index] = inputForm;
                    useDataState.setState({inputForms: [...inputForms]});
                }
            }
        },

        upsertInputEnum: async (inputEnum: InputEnum) => {
            const isNew = inputEnum.id == NEW_ELEMENT_ID;
            const inputEnumObj: Partial<InputEnum> = {...inputEnum};

            if (isNew) {
                delete inputEnumObj.id;
            }

            const resp = await fetchEx(getEndpointUrl(Endpoint.input_enum), "PUT", JSON.stringify(inputEnumObj));
            const inputEnumJson = await resp.json();
            inputEnum.id = inputEnumJson.id;

            const inputEnums = useDataState.getState().inputEnums;

            if (isNew) {
                inputEnums.push(inputEnum);
                useDataState.setState({inputEnums: [...inputEnums]});
            } else {
                const index = inputEnums.findIndex((item: InputEnum) => item.id === inputEnum.id);
                if (index !== -1) {
                    inputEnums[index] = inputEnum;
                    useDataState.setState({inputEnums: [...inputEnums]});
                }
            }
        },

        deleteInputField: async (inputField: InputField) => {
            if(inputField.id === NEW_ELEMENT_ID)
                throw new Error("Cannot delete new input field.");

            await fetchEx(getEndpointUrl(Endpoint.input_field) + "/" + inputField.id, "DELETE");

            const inputFields = useDataState.getState().inputFields;
            const index = inputFields.findIndex((item: InputField) => item.id === inputField.id);
            if(index !== -1) {
                inputFields.splice(index, 1);
                useDataState.setState({inputFields: [...inputFields]});
            }
        },

        deleteInputForm: async (inputForm: InputForm) => {
            if(inputForm.id === NEW_ELEMENT_ID)
                throw new Error("Cannot delete new input form.");

            await fetchEx(getEndpointUrl(Endpoint.input_form) + "/" + inputForm.id, "DELETE");

            const inputForms = useDataState.getState().inputForms;
            const index = inputForms.findIndex((item: InputForm) => item.id === inputForm.id);
            if(index !== -1) {
                inputForms.splice(index, 1);
                useDataState.setState({inputForms: [...inputForms]});
            }
        },

        deleteInputEnum: async (inputEnum: InputEnum) => {
            if(inputEnum.id === NEW_ELEMENT_ID)
                throw new Error("Cannot delete new input enum.");

            await fetchEx(getEndpointUrl(Endpoint.input_enum) + "/" + inputEnum.id, "DELETE");

            const inputEnums = useDataState.getState().inputEnums;
            const index = inputEnums.findIndex((item: InputEnum) => item.id === inputEnum.id);
            if(index !== -1) {
                inputEnums.splice(index, 1);
                useDataState.setState({inputEnums: [...inputEnums]});
            }
        }
    })
};


export default useApi;