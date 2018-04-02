﻿using UnityEngine;
using System.Collections;
using UnityEngine.UI;
using System.Collections.Generic;
using UnityEngine.EventSystems;
using ParallelProg.UI;

public class Load_GamePhaseBehavior : GamePhaseBehavior {

	[System.Serializable]
	public class Load_UI
	{
		public GameObject loadPhaseUI;

        public RectTransform requiredLevelContainer, requiredTransform;
        public RectTransform optionalLevelContainer, optionalTransform;
        public RectTransform previousContainer, previousTransform;

        public GameObject levelButtonPrefab;
        public Button exitLevelSelectionButton;
        [SerializeField] public UIOverlay levelLoadingOverlay;
        public Button requiredLevelsButton, optionalLevelsButton, previousLevelsButton;
	}
	public Load_UI loadUI;


	public override void BeginPhase()
	{
		
        foreach (Transform child in loadUI.requiredLevelContainer)
        {
            GameObject.Destroy(child.gameObject);
        }
        foreach (Transform child in loadUI.optionalLevelContainer)
        {
            GameObject.Destroy(child.gameObject);
        }
        foreach (Transform child in loadUI.previousContainer)
        {
            GameObject.Destroy(child.gameObject);
        }

        foreach (LevelReferenceObject lr in GameManager.Instance.GetDataManager().levRef.levels.required)
        {
            SetupLevelButton(lr, loadUI.requiredLevelContainer);
        }
        foreach (LevelReferenceObject lr in GameManager.Instance.GetDataManager().levRef.levels.optional)
        {
            SetupLevelButton(lr, loadUI.optionalLevelContainer);
        }
        if (GameManager.Instance.GetDataManager().levRef.levels.previous != null)
        {
            foreach (LevelReferenceObject lr in GameManager.Instance.GetDataManager().levRef.levels.previous)
            {
                SetupLevelButton(lr, loadUI.previousContainer);
            }
        }

        if (!GameManager.Instance.hideTestsForBuild)
        {
            //AddPCGButton();
        }

        loadUI.levelLoadingOverlay.ClosePanel(true);

        loadUI.loadPhaseUI.SetActive(true);

        loadUI.exitLevelSelectionButton.onClick.AddListener(() => GameManager.Instance.SetGamePhase(GameManager.GamePhases.StartScreen));

        loadUI.requiredLevelsButton.onClick.RemoveAllListeners();
        loadUI.requiredLevelsButton.onClick.AddListener(() => 
        {
            TriggerPanelSwap(true, false, false);
        });

        loadUI.optionalLevelsButton.onClick.RemoveAllListeners();
        loadUI.optionalLevelsButton.onClick.AddListener(() =>
        {
            TriggerPanelSwap(false, false, true);
        });

        loadUI.previousLevelsButton.onClick.RemoveAllListeners();
        loadUI.previousLevelsButton.onClick.AddListener(() =>
        {
            TriggerPanelSwap(false, true, false);
        });

        TriggerPanelSwap(true, false, false);
    }

    void TriggerPanelSwap(bool requiredPanel, bool previousPanel, bool optionalPanel )
    {
        loadUI.requiredLevelsButton.gameObject.SetActive(!requiredPanel);
        loadUI.requiredTransform.gameObject.SetActive(requiredPanel);

        loadUI.previousLevelsButton.gameObject.SetActive(!previousPanel);
        loadUI.previousTransform.gameObject.SetActive(previousPanel);

        loadUI.optionalLevelsButton.gameObject.SetActive(!optionalPanel);
        loadUI.optionalTransform.gameObject.SetActive(optionalPanel);
    }

    void SetupLevelButton(LevelReferenceObject lr, Transform container)
    {
        char[] trimArray = new char[5] { 'L', 'l', 'e', 'v', ' ' };
        string levelName = lr.file;
        GameObject g = Instantiate(loadUI.levelButtonPrefab) as GameObject;
        LevelButtonBehavior buttonInstance = g.GetComponent<LevelButtonBehavior>();
        if (buttonInstance != null)
        {
            buttonInstance.SetLevelSprite(false, lr.completionRank > 0);
            buttonInstance.SetLevelRank(lr.GetLevelCompletionRank());
        }

        Button gButton = g.GetComponent<Button>();
        g.transform.SetParent(container);
        g.transform.localScale = Vector3.one;
        Text gText = g.GetComponentInChildren<Text>();
        gText.text = levelName.TrimStart(trimArray);
        gButton.onClick.AddListener(() => LoadButtonBehavior(levelName));
    }

    public void AddPCGButton(){
			GameObject g = Instantiate(loadUI.levelButtonPrefab) as GameObject;
			Button gButton = g.GetComponent<Button>();
			g.transform.SetParent( loadUI.requiredLevelContainer );
			g.transform.localScale = Vector3.one;
			Text gText = g.GetComponentInChildren<Text>();
			gText.text = "PCG";
			gButton.onClick.AddListener( ()=> LoadPCGBehavior() );
	}

	public override void UpdatePhase()
	{
	}

	public override void EndPhase()
	{
        if(loadUI.levelLoadingOverlay.isOpen) loadUI.levelLoadingOverlay.ClosePanel();
        loadUI.loadPhaseUI.SetActive(false);
	}

	public void LoadButtonBehavior( string levelName ) 
	{
        loadUI.levelLoadingOverlay.OpenPanel();
        GameManager.Instance.TriggerLoadLevel( DataManager.LoadType.RESOURCES, levelName );
	}

    public void LoadButtonBehavior(LevelReferenceObject levelReference)
    {
        loadUI.levelLoadingOverlay.OpenPanel();
        GameManager.Instance.TriggerLoadLevel(levelReference);
    }

    public void LoadPCGBehavior()
    {
        loadUI.levelLoadingOverlay.OpenPanel();
        GameManager.Instance.TriggerPCG();
    }
}
