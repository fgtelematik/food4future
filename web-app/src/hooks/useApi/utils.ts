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
import {ApiRequestError} from "./ApiRequestError.ts";
import useApi, {Endpoint} from "./index.ts";
import {FoodImage} from "../../models/main.ts";
import {useDataState} from "../useState/useData.ts";
import {useSessionState} from "../useState/useSessionState.ts";



export function getEndpointUrl(endpoint: Endpoint) {
    const apiUrl = usePersistentState.getState().getApiUrl();
    const trailingSlash = apiUrl.endsWith('/') ? '' : '/';
    return apiUrl + trailingSlash + endpoint;
}

export function convertDatesRecursive(obj: any): any {
    if (typeof obj === "string" && !isNaN(Date.parse(obj))) {
        obj = obj.substring(0, 19) + "Z"; // convert to iso date string to format: "2021-01-01T00:00:00Z"
        return new Date(obj);
    } else if (typeof obj === "object") {
        if (Array.isArray(obj)) {
            return obj.map(convertDatesRecursive);
        } else {
            const newObj: any = {};
            for (const key in obj) {
                newObj[key] = convertDatesRecursive(obj[key]);
            }
            return newObj;
        }
    } else {
        return obj;
    }
}

export async function getJsonObj<T>(
    url: string,
    method: string = "GET",
    body: BodyInit | null = null,
    extras: RequestInit = {},
    no_auth: boolean = false
): Promise<T> {
    try {
        const resp = await fetchEx(url, method, body, extras, no_auth);
        let obj = await resp.json();
        // convert string elements of obj which are a valid iso date to Date objects
        obj = convertDatesRecursive(obj);

        return obj;
    } catch (e) {
        throw (e);
    }
}


export async function fetchEx(
    url: string,
    method: string = "GET",
    body: BodyInit | null = null,
    extras: RequestInit = {},
    no_auth: boolean = false,
    no_global_error: boolean = false,
    timeout: number = usePersistentState.getState().apiTimeout
) : Promise<Response> {
    const token = usePersistentState.getState().apiToken;
    let controller = new AbortController()
    setTimeout(() => controller.abort(), timeout);

    const init: RequestInit = {method: method, signal: controller.signal, body: body, ...extras};

    const headers = init.headers as { [key: string]: string } ?? {};

    if (!!token && !no_auth) // Add auth token if available
    {
        headers["Authorization"] = `Bearer ${token}`;
    }

    // Set JSON as default content type if not set
    if (!headers["Content-Type"])
        headers["Content-Type"] = "application/json";

    init.headers = {...headers};
    
    let resp : Response | null = null;
    
    const handleError = (error: any) =>
    {
        if (!no_global_error) {
            if(error?.statusCode === 401)
            {
                useApi().logout().then(() => {
                    useSessionState.setState({globalError: "Your session has expired. Please refresh the page and log in again."});
                });
            } else
                useSessionState.setState({globalError: error?.toString() ?? "Unknown error"});
        }
        throw error;
    }
    
    useDataState.setState({isLoading: true});
    try {
        resp = await fetch(url, init);

        if (!resp.ok) {
            handleError(new ApiRequestError(resp));
        }
    } catch (e: unknown) {
        handleError(e);
    } finally {
        useDataState.setState({isLoading: false});
    }
    
    return resp!; 
        // this case will never happen, it's just to satisfy the compiler
        // (if the fetch fails, the function will throw an error)
}


export function uploadFile<T>(url: string, file_field_name: string, file: File, extra_data?: { [key: string] : any }, onUploadProgress?: (progress: number) => void): Promise<T> {
    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();

        xhr.open("POST", url);

        const authToken = usePersistentState.getState().apiToken;
        if (authToken) {
            xhr.setRequestHeader("Authorization", `Bearer ${authToken}`);
        }

        const formData = new FormData();
        formData.append(file_field_name, file);
        
        if(extra_data) {
            Object.keys(extra_data).forEach(key => {
                formData.append(key, JSON.stringify(extra_data[key]));
            });
        }

        xhr.upload.addEventListener("progress", (event) => {
            if (event.lengthComputable && onUploadProgress) {
                const progress = event.loaded / event.total;
                onUploadProgress(progress);
            }
        });

        xhr.addEventListener("load", () => {
            if (xhr.status >= 200 && xhr.status < 300) {
                resolve(JSON.parse(xhr.response) as T);
            } else {
                reject(new Error(`Server returned status code ${xhr.status}`));
            }
        });

        xhr.addEventListener("error", () => {
            reject(new Error("An error occurred while uploading the file"));
        });

        xhr.send(formData);
    });
}

export function saveBlob(blob: Blob, fileName: string) {
    const a = document.createElement('a');
    a.href = window.URL.createObjectURL(blob);
    a.download = fileName;
    a.dispatchEvent(new MouseEvent('click'));
}

export function downloadFileFromPostRequest(url: string, extra_data?: { [key: string] : any }):Promise<void> {
    // took some inspirations from: https://stackoverflow.com/a/44435573/5106474

    return new Promise((resolve, reject) => {
        const xhr = new XMLHttpRequest();

        xhr.open("POST", url);
        xhr.responseType = 'blob';

        const authToken = usePersistentState.getState().apiToken;
        if (authToken) {
            xhr.setRequestHeader("Authorization", `Bearer ${authToken}`);
        }

        if(extra_data) {
            xhr.setRequestHeader("Content-Type", "application/json");
        }

        //
        // const formData = new FormData();
        //
        // if (extra_data) {
        //     Object.keys(extra_data).forEach(key => {
        //         formData.append(key, JSON.stringify(extra_data[key]));
        //     });
        // }

        xhr.addEventListener("load", (e) => {
            if (xhr.status >= 200 && xhr.status < 300) {
                const blob = xhr.response as Blob;
                const contentDispo = xhr.getResponseHeader('Content-Disposition');
                // https://stackoverflow.com/a/23054920/
                let fileName="file";
                if(contentDispo) {
                    const res = contentDispo.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/);
                    if (res && res[1]) {
                        fileName = res[1].replace(/['"]/g, '');
                    }
                }
                saveBlob(blob, fileName);
                resolve();
            } else {
                reject(new Error(`Server returned status code ${xhr.status}`));
            }
        });

        xhr.addEventListener("error", () => {
            reject(new Error("An error occurred while requesting the file download."));
        });

        xhr.send(extra_data ? JSON.stringify(extra_data) : "");
    });
}