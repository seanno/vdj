//
// MAINFX.JAVA
//

package com.shutdownhook.vdj.standalone.fx;

import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import com.shutdownhook.toolbox.Easy;
import com.shutdownhook.vdj.standalone.Server;

public class MainFX extends Application {

	private final static int HGAP = 10;
	private final static int VGAP = 10;

	private final static int WINDOW_WIDTH = 550;
	private final static int WINDOW_HEIGHT = 250;
	private final static int PADDING_SIZE = 20;
	private final static int WRAPPING_WIDTH = 500;
	
	private final static String HEADER_TEXT = "VDJ v1.0";
	private final static int HEADER_SIZE = 20;

	private final static String QUIT_TEXT = "Exit";

	private final static String DOC_TEXT = "Documentation";
	private final static String DOC_URL = "https://docs.google.com/viewer?url=https://github.com/seanno/vdj/raw/main/docs/VDJ%20User%20Manual.pdf";

	private final static String OPEN_TEXT = "Start Using VDJ";
	private final static String OPEN_FORMAT = "http://localhost:%d/";

	private final static String SOURCE_TEXT = "Source Code";
	private final static String SOURCE_URL = "https://github.com/seanno/vdj";

	private final static String ABOUT_RESOURCE = "about.txt";
	
	// +-------------+
	// | app control |
	// +-------------+
	
    @Override
    public void start(Stage stage) {

		startServer();

		// text

		String about = null;
		try { about = Easy.stringFromResource(ABOUT_RESOURCE); }
		catch (Exception e)  { /* won't happen */ }

        Text txtAbout = new Text(about);
		txtAbout.setWrappingWidth(WRAPPING_WIDTH);
		
        Text txtHeader = new Text(HEADER_TEXT);
		txtHeader.setFont(new Font(HEADER_SIZE));

		// buttons
		
		Button btnOpen = new Button(OPEN_TEXT);
		btnOpen.setDefaultButton(true);
		btnOpen.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				String url = String.format(OPEN_FORMAT, cfg.WebServer.Port);
				getHostServices().showDocument(url);
			}
		});

		Button btnDocs = new Button(DOC_TEXT);
		btnDocs.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				getHostServices().showDocument(DOC_URL);
			}
		});

		Button btnSource = new Button(SOURCE_TEXT);
		btnSource.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				getHostServices().showDocument(SOURCE_URL);
			}
		});

		Button btnQuit = new Button(QUIT_TEXT);
		btnQuit.setOnAction(new EventHandler<ActionEvent>() {
			public void handle(ActionEvent e) {
				Platform.exit();
			}
		});

		HBox hbox = new HBox(HGAP);
		hbox.getChildren().addAll(btnOpen, btnDocs, btnSource, btnQuit);

		// scene

		FlowPane pane = new FlowPane(Orientation.VERTICAL, HGAP, VGAP,
									 txtHeader, txtAbout, hbox);

		pane.setPadding(new Insets(PADDING_SIZE));
		
        Scene scene = new Scene(pane, WINDOW_WIDTH, WINDOW_HEIGHT);
		
        stage.setScene(scene);
        stage.show();
    }

	@Override
	public void stop() {
		stopServer();
	}
	
    public static void main(String[] args) {
        launch();
    }

	// +--------+
	// | server |
	// +--------+

	
	private void startServer() {
		try {
			cfg = DesktopConfig.getConfig();
			Easy.configureLoggingProperties(cfg.LoggingConfigPath);
			server = new Server(cfg);
			server.start();
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "startServer", true));
		}
	}


	private void stopServer() {
		try {
			if (server != null) server.close();
		}
		catch (Exception e) {
			log.severe(Easy.exMsg(e, "stopServer", true));
		}
	}

	private Server server;
	private Server.Config cfg;

	private final static Logger log = Logger.getLogger(MainFX.class.getName());
}
