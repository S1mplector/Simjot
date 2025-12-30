/**
 * 
 */
/**
 * 
 */
module Simjot {
	requires transitive java.desktop;
    requires java.net.http;
    requires org.apache.pdfbox;
	
	// Sim 
	exports main.core.service;
	exports main.core.model;
	exports main.core.sim.prefs;
	exports main.core.sim.api;
	exports main.core.sim.engine;
	exports main.core.sim.data;
	exports main.core.sim.persona;
	exports main.core.sim.llm.api;
	exports main.core.sim.llm.openai;
	exports main.core.sim.llm.ollama;
	exports main.core.sim.llm.prompt;

    // Core exporting utilities
    exports main.core.export;

	// Infrastructure
	exports main.infrastructure.monitoring;
	exports main.infrastructure.backup;
	exports main.infrastructure.io;
	
	// Core poetry utilities
	exports main.core.poetry;

	// Dialog
	exports main.ui.dialog.config;
	exports main.ui.dialog.confirmation;
	exports main.ui.dialog.input;
	exports main.ui.dialog.setup;
	exports main.ui.dialog.message;
	exports main.ui.dialog.utils;
    exports main.ui.dialog.export;

	// App and UI modules
	exports main.ui.app;
	exports main.ui.components.buttons;
	exports main.ui.animations.transitions;
	exports main.ui.components;
	exports main.ui.components.icons;
	exports main.ui.features.widgets;
	exports main.ui.theme.aero;
    exports main.ui.theme;
	exports main.ui.features.drawing;
	exports main.ui.sim.overlay;

	// Feature panels
	exports main.ui.features.entries;
	exports main.ui.features.gallery;
	exports main.ui.features.quicksettings;
	exports main.ui.features.home;
	// Poetry workspace UI components
	exports main.ui.features.poetry;
}