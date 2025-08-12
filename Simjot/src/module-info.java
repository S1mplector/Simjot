/**
 * 
 */
/**
 * 
 */
module Simjot {
	requires transitive java.desktop;
	
	exports main.core.service;
	exports main.core.model;

	// Infrastructure
	exports main.infrastructure.monitoring;
	exports main.infrastructure.backup;
	exports main.infrastructure.io;

	// Dialog
	exports main.ui.dialog.config;
	exports main.ui.dialog.confirmation;
	exports main.ui.dialog.input;
	exports main.ui.dialog.setup;
	exports main.ui.dialog.message;
	exports main.ui.dialog.utils;

	// App and UI modules
	exports main.ui.app;
	exports main.ui.components.buttons;
	exports main.ui.animations.transitions;
	exports main.ui.components;
	exports main.ui.components.icons;
	exports main.ui.features.widgets;
	exports main.ui.theme.aero;
	exports main.ui.features.drawing;

	// Feature panels
	exports main.ui.features.entries;
	exports main.ui.features.gallery;
	exports main.ui.features.quicksettings;
	exports main.ui.features.home;
}