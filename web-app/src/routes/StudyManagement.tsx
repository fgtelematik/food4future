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
import {
    Accordion,
    AccordionDetails,
    AccordionSummary,
    Button,
    CircularProgress,
    FormControl,
    IconButton,
    InputLabel,
    MenuItem,
    Select,
    TextField
} from "@mui/material";
import "../style/StudyManagement.css";
import AddIcon from '@mui/icons-material/Add';
import DeleteIcon from '@mui/icons-material/Delete';
import {NEW_ELEMENT_ID, useDataState} from "../hooks/useState/useData.ts";
import useApi from "../hooks/useApi";
import UseApi from "../hooks/useApi";
import AlertDialog from "../components/AlertDialog.tsx";
import {usePersistentState} from "../hooks/useState/usePersistentState.ts";
import {locStr} from "../utils.ts";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import {StudyManagementSectionProps} from "../components/StudyManagement/interface.ts";
import {GeneralStudyManagementSection} from "../components/StudyManagement/GeneralSection.tsx";
import {RegistrationMailSection} from "../components/StudyManagement/RegistrationMailSection.tsx";
import {AppSection} from "../components/StudyManagement/AppSection.tsx";
import {StaffSection} from "../components/StudyManagement/StaffSection.tsx";
import {useSessionState} from "../hooks/useState/useSessionState.ts";
import {ObjectId} from "../models/main.ts";
import {list} from "postcss";
import {Study} from "../models/study.ts";
import {GarminSensorSection} from "../components/StudyManagement/GarminSensorSection.tsx";
import {CosinussSensorSection} from "../components/StudyManagement/CosinussSensorSection.tsx";

type ConfigSection = {
    title: string,
    component: FC<StudyManagementSectionProps>
}

type ConfigSectionProps = {
    title: string,
    children: React.ReactNode
    expanded: boolean
    onExpand: () => void
    error?: boolean
}

const ConfigSection: FC<ConfigSectionProps> = ({title, children, expanded, onExpand, error}) => {
    return (
        <Accordion expanded={expanded} onChange={(e, expanded) => expanded && onExpand()}>
            <AccordionSummary className={"section-header"} expandIcon={<ExpandMoreIcon/>}>
                <span className={error ? "section-error" : ""}>{title}</span>
            </AccordionSummary>
            <AccordionDetails className={"section-content"}>
                {children}
            </AccordionDetails>
        </Accordion>
    )
}

const sections: ConfigSection[] = [
    {
        title: "General",
        component: GeneralStudyManagementSection
    },
    {
        title: "App Configuration",
        component: AppSection
    },
    {
        title: "Registration E-Mail Templates",
        component: RegistrationMailSection
    },
    {
        title: "cosinuss° One Sensor Configuration",
        component: CosinussSensorSection
    },
    // {
    //     title: "Garmin Wearable Sensor Configuration",
    //     component: GarminSensorSection
    // },
    {
        title: "Staff Management",
        component: StaffSection
    },
]

export const StudyManagementView = () => {
    const [loading, setLoading] = useState<boolean>(false);
    const studies = useDataState(state => state.studies);
    let studyId = usePersistentState(state => state.currentStudyId);
    const [newStudyTitle, setNewStudyTitle] = useState<string>("My Study");
    const [newStudyDialogOpen, setNewStudyDialogOpen] = useState<boolean>(false);
    const createNewStudy = useDataState(state => state.createNewStudy);
    const [expandedSectionIndex, setExpandedSectionIndex] = useState<number>(0);

    const study = useSessionState(state => state.currentEditStudy);
    const setStudy = useSessionState(state => state.setCurrentEditStudy);
    

    const sectionErrors = useSessionState(state => state.editStudySectionErrors);
    const setSectionErrors = useSessionState(state => state.setEditStudySectionErrors);

    useEffect(() => {
        async function fetchData() {
            setLoading(true);
            try {
                await useApi().fetchStudies();
                await UseApi().fetchFoodEnums();
                await useApi().fetchAllFormData();
            } catch (e: any) {
                console.error(e);
            } finally {
                setLoading(false);
            }
        }

        fetchData();
        
    }, []);

    if (!studyId && studies.length > 0)
        studyId = studies[0].id!;

    useEffect(() => {
        if (study?.modified) {
            // prevent discarding current modifications on reload
            return;
        }
        
        setSectionErrors({});

        if (!studyId) {
            setStudy(undefined);
            return;
        }

        const loadedStudy = studies.find(study => study.id === studyId);
        if (loadedStudy) {
            setStudy({...loadedStudy});
        }

    }, [studies, studyId]);
    
    const onCreateNewStudy = () => {
        const doCreate = () => {
            setNewStudyTitle("My Study");
            createNewStudy(newStudyTitle);
            setExpandedSectionIndex(0);
        }

        if (!study?.modified) {
            doCreate();
            return;
        }

        useSessionState.setState({
            alertDialog: {
                title: "Unsaved Changes",
                content: "The current study has been modified. The current changes will be lost. Continue creating a new study?",
                confirmText: "Yes",
                cancelText: "Cancel",
                onConfirm: () => {
                    setStudy(undefined);
                    doCreate();
                },
            }
        });
    };
    
    const onSaveStudy = async () => {
        if (!study)
            return;
        try {
            setLoading(true);
            study.modified = false;
            setStudy({...study});
            await useApi().upsertStudy(study);
        } catch (e: any) {
            setStudy({...study, modified: true});
            console.error(e);
        } finally {
            setLoading(false);
        }
    }

    const doDeleteStudy = async (switchToStudyId? : ObjectId) => {
        if (!study)
            return;
        
        try {
            setLoading(true);

            await useApi().deleteStudy(study.id);
            const updatedStudies = useDataState.getState().studies;
            setStudy(undefined);
            
            if(switchToStudyId) {
                usePersistentState.setState({currentStudyId: switchToStudyId});
            } else {
                if (updatedStudies.length > 0)
                    usePersistentState.setState({currentStudyId: updatedStudies[0].id});
                else
                    usePersistentState.setState({currentStudyId: undefined});
            }
            
            await useApi().fetchStudies();
        } catch (e: any) {
            console.error(e);
        } finally {
            setLoading(false);
        }
    }
    
    const requestDeleteStudy = async () => {
        if (!study)
            return;
        
        if(study.id === NEW_ELEMENT_ID && !study.modified) {
            await doDeleteStudy();
            return;
        }
        
        useSessionState.setState({
            alertDialog: {
                title: "Delete Study",
                content: `Do you really want to delete the study "${study.title}"?`,
                confirmText: "Yes",
                cancelText: "Cancel",
                onConfirm: () => doDeleteStudy(),
            }
        });
    }
    
    const onSwitchStudy = (studyId: ObjectId) => {
        if(study?.id === studyId) 
            return;
        
        if (study?.modified) {
            useSessionState.setState({
                alertDialog: {
                    title: "Unsaved Changes",
                    content: "The current study has been modified. The current changes will be lost. Continue?",
                    confirmText: "Yes",
                    cancelText: "Cancel",
                    onConfirm: async () => {
                        if(study.id == NEW_ELEMENT_ID) {
                            await doDeleteStudy(studyId);
                            return;
                        }
                        setStudy(undefined);
                        usePersistentState.setState({currentStudyId: studyId});
                    },
                }
            });
        } else {
            usePersistentState.setState({currentStudyId: studyId});
        }
    }

    if (loading) {
        return (
            <div className={"study-management"}>
                <CircularProgress/>
                <p>Loading study data...</p>
            </div>
        )
    }
    
    const error = Object.keys(sectionErrors).map(key => sectionErrors[key]).some(error => error);
    
    return (
        <div className={"study-management"}>

            {newStudyDialogOpen && <AlertDialog
                title={"Create new study"}
                confirmDisabled={newStudyTitle.trim().length < 3}
                confirmText={"Create"}
                cancelText={"Cancel"}
                onConfirm={() => onCreateNewStudy()}
                onClose={() => setNewStudyDialogOpen(false)}
            >
                <div className={"new-study-dialog-form"}>
                    <p>Please enter a title for the new study:</p>
                    <TextField
                        autoFocus
                        onFocus={event => {
                            event.target.select();
                        }}
                        margin="dense"
                        id="study-title"
                        label="Study Title"
                        type="text"
                        fullWidth
                        value={newStudyTitle}
                        onChange={(e) => setNewStudyTitle(e.target.value)}
                    />
                </div>
            </AlertDialog>}

            <div className={"study-selector"}>
                {studies.length > 0 ?
                    <div className={"selector-input"}>
                        <InputLabel id="select-study-label">
                            Select Study:
                        </InputLabel>
                        <FormControl sx={{m: 1, minWidth: 200}} size="small">
                            <Select
                                labelId={"select-study-label"}
                                value={studyId}>
                                {
                                    studies.map(listedStudy => {
                                        let studyName= locStr(listedStudy.id == study?.id ? study?.title : listedStudy.title)
                                        if(!studyName) studyName = "(unnamed study)";
                                        if(listedStudy.id == NEW_ELEMENT_ID) studyName += " (new)";
                                        
                                        return (
                                            <MenuItem
                                                key={listedStudy.id}
                                                value={listedStudy.id}
                                                onClick={() => onSwitchStudy(listedStudy.id)} 
                                            >
                                                {studyName}
                                            </MenuItem>
                                        )
                                    })
                                }
                            </Select>
                        </FormControl>
                        <IconButton onClick={requestDeleteStudy} ><DeleteIcon/></IconButton>
                        <Button onClick={() => setNewStudyDialogOpen(true)}><AddIcon/>New...</Button>
                    </div>
                    :
                    <>
                        <p><i>You have not set up any studies yet...</i></p>
                        <Button
                            onClick={() => setNewStudyDialogOpen(true)}
                            disabled={loading}>
                            <AddIcon/>Create New Study
                        </Button>
                    </>
                }
                {(study?.modified || study?.id === NEW_ELEMENT_ID) && <p className={"unsaved-warning"}>
                    {   study?.id === NEW_ELEMENT_ID ?
                        "The study has not yet been created!" : 
                        "The modifications have not yet been saved!"
                    }
                </p>}
            </div>


            {studies.length > 0 &&
                <>
                    <div className={"config-sections"}>
                        {
                            sections.map((section, index) => (
                                <ConfigSection
                                    key={index}
                                    title={section.title}
                                    expanded={index === expandedSectionIndex}
                                    onExpand={() => setExpandedSectionIndex(index)}
                                    error={sectionErrors[section.title]}
                                >
                                    {/*{study && section.component({study, updateStudy: (study, validData) => setStudy(study)})}*/}
                                    {study &&
                                        <section.component
                                            key={study.id + "_" + index}
                                            study={study}
                                            updateStudy={(study, validData, unmodified) => {
                                                if(!unmodified)
                                                    study.modified = true;
                                                setStudy(study);
                                                setSectionErrors({...sectionErrors, [section.title] : !validData});
                                            }
                                            }/>
                                    }
                                </ConfigSection>
                            ))
                        }

                        <div className={"study-management-buttons"}>
                            {error && <p className={"section-error"}><i>Please correct the highlighted entries.</i></p>}
                            <Button
                                variant={"contained"}
                                onClick={onSaveStudy}
                                disabled={!study?.modified || error}
                            >{study?.id === NEW_ELEMENT_ID ? "Create Study" : "Save Study"}</Button>
                        </div>

                    </div>
                </>
            }
        </div>
    )

}