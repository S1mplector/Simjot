/**
 * 
 */
/**
 * 
 */
module Simjot {
	requires java.desktop;
	
	exports main.model;
	exports main.core.service;

	// Infrastructure
	exports main.infrastructure.monitoring;
	exports main.infrastructure.backup;
	exports main.infrastructure.io;

	exports main.ui;
	exports main.ui.panels;
	exports main.ui.buttons;
	exports main.transitions;
	exports main.dialog;
}