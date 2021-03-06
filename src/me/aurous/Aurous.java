package me.aurous;

import java.awt.Rectangle;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.Format.Field;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

import me.aurous.config.AppConstants;
import me.aurous.exceptions.ExceptionWidget;
import me.aurous.js.AurousBridge;
import me.aurous.local.database.DatabaseManager;
import me.aurous.local.media.watcher.WatcherService;
import me.aurous.local.settings.AurousSettings;
import me.aurous.ui.window.AurousWindowManager;
import me.aurous.utils.OSUtils;
import me.aurous.utils.Utils;

import com.teamdev.jxbrowser.chromium.Browser;
import com.teamdev.jxbrowser.chromium.BrowserContext;
import com.teamdev.jxbrowser.chromium.BrowserPreferences;
import com.teamdev.jxbrowser.chromium.CloseStatus;
import com.teamdev.jxbrowser.chromium.FileChooserMode;
import com.teamdev.jxbrowser.chromium.FileChooserParams;
import com.teamdev.jxbrowser.chromium.LoggerProvider;
import com.teamdev.jxbrowser.chromium.PopupContainer;
import com.teamdev.jxbrowser.chromium.PopupHandler;
import com.teamdev.jxbrowser.chromium.PopupParams;
import com.teamdev.jxbrowser.chromium.events.FinishLoadingEvent;
import com.teamdev.jxbrowser.chromium.events.LoadAdapter;
import com.teamdev.jxbrowser.chromium.javafx.BrowserView;
import com.teamdev.jxbrowser.chromium.javafx.DefaultDialogHandler;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import me.aurous.jus.BlockingChecker;

public class Aurous extends Application {

    
	
	private void config() {
	//	try {
	//	extractUpdater();
	//	} catch (IOException e) {
	//		// TODO Auto-generated catch block
	//		e.printStackTrace();
	//	}
		System.setProperty("awt.useSystemAAFontSettings", "on");
		System.setProperty("swing.aatext", "true"); // enable System aa
		final AurousSettings settings = new AurousSettings();
		final File settingsFile = new File(settings.settingsPath);
		if (!settingsFile.exists()) {
			settings.saveDefaults();
		}
		Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
		if (AppConstants.REMOTE_DEBUG) {
			final String[] switches = { "--remote-debugging-port=9222"};
			BrowserPreferences.setChromiumSwitches(switches);
		}

		final Properties props = System.getProperties();
        props.setProperty("jxbrowser.chromium.dnd.enabled", "false");
		if (!AppConstants.VERBOSE_LOG) {
			LoggerProvider.setLevel(Level.OFF);
		}
		
	

		switch (OSUtils.getOS()) {
		case WINDOWS:
			System.setProperty("os.name", "Windows 8.1");
			System.setProperty("jxbrowser.chromium.dir", "./windows/");
			break;
		case LINUX:
			System.setProperty("jxbrowser.chromium.dir", "linux");
			break;
		case OSX:
			System.setProperty("apple.laf.useScreenMenuBar", "false");
			// set the name of the application menu item
						System.setProperty(
								"com.apple.mrj.application.apple.menu.about.name", "Aurous");
						final com.apple.eawt.Application macApp = com.apple.eawt.Application
								.getApplication();
						macApp.setDockIconImage(new ImageIcon(this.getClass().getResource(
								"/resources/logo.png")).getImage());
			System.setProperty("jxbrowser.chromium.dir", "mac");
			break;
		}
	}

	@Override
	public void start(final Stage primaryStage) throws MalformedURLException {
		try {
			System.setProperty("file.encoding","UTF-8");
			java.lang.reflect.Field charset = Charset.class.getDeclaredField("defaultCharset");
			charset.setAccessible(true);
			charset.set(null,null);
		} catch (NoSuchFieldException | SecurityException
				| IllegalArgumentException | IllegalAccessException e1) {
				System.out.println("Unable to override system encoding");
		}
		System.out.println( System.getProperty("file.encoding"));
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException
				| IllegalAccessException | UnsupportedLookAndFeelException e) {
			final ExceptionWidget widget = new ExceptionWidget(e);
			widget.showWidget();
		}

		AppConstants.dataPath();
		DatabaseManager.connect();
		config();

		primaryStage.setTitle("Aurous");

		if (AppConstants.BORDERLESS) {
			primaryStage.initStyle(StageStyle.TRANSPARENT);
		} else {
			primaryStage.initStyle(StageStyle.UNDECORATED);
		}

		final String index = new File(AppConstants.DEFAULT_PATH).toURI()
				.toURL().toString();

		final Browser browser = new Browser(BrowserContext.defaultContext());
		final WatcherService service = new WatcherService(browser);
		service.startService();
		final BrowserPreferences preferences = browser.getPreferences();
		preferences.setJavaScriptEnabled(true);
		final BrowserView browserView = new BrowserView(browser);
		// browserView.setDragAndDropEnabled(false);

		final StackPane pane = new StackPane();
		final AurousBridge bridge = new AurousBridge(primaryStage, browser);

		pane.getChildren().add(browserView);

		final Scene scene = new Scene(pane, 1280, 720, Color.TRANSPARENT);

		primaryStage.setScene(scene);

		AurousWindowManager.addResizeListener(primaryStage, browserView);
		primaryStage.centerOnScreen();
		primaryStage.show();
		browser.setDialogHandler(new DefaultDialogHandler(browserView) {
			@Override
			public CloseStatus onFileChooser(final FileChooserParams params) {
				if (params.getMode() == FileChooserMode.Open) {
					Platform.runLater(() -> {

						final DirectoryChooser chooser = new DirectoryChooser();
						chooser.setTitle("Select ");
						final File defaultDirectory = new File((System
								.getProperty("user.home")));
						chooser.setInitialDirectory(defaultDirectory);
						final File selectedDirectory = chooser
								.showDialog(primaryStage);
						if (selectedDirectory != null) {
							final String script = String.format(
									"settings.addScanPath('%s');",
									selectedDirectory.getAbsolutePath()
											.replace("\\", "\\\\"));
							browser.executeJavaScript(script);

						}
					});
				}
				return CloseStatus.CANCEL;
			}
		});
		browser.addLoadListener(new LoadAdapter() {
			@Override
			public void onFinishLoadingFrame(final FinishLoadingEvent event) {

				if (AppConstants.INTERNAL_DEBUG) {
					browser.executeJavaScript("if (!document.getElementById('FirebugLite')){E = document['createElement' + 'NS'] && document.documentElement.namespaceURI;E = E ? document['createElement' + 'NS'](E, 'script') : document['createElement']('script');E['setAttribute']('id', 'FirebugLite');E['setAttribute']('src', 'https://getfirebug.com/' + 'firebug-lite.js' + '#startOpened');E['setAttribute']('FirebugLite', '4');(document['getElementsByTagName']('head')[0] || document['getElementsByTagName']('body')[0]).appendChild(E);E = new Image;E['setAttribute']('src', 'https://getfirebug.com/' + '#startOpened');}");
				}

			}
		});
	

		// browser.loadURL("http://104.131.187.115/nwtests/index.html");
		// browser.loadURL("http://vk.me");
		browser.loadURL(index);
		bridge.setupBridge();

		final String remoteDebuggingURL = browser.getRemoteDebuggingURL();
		System.out.println(remoteDebuggingURL);
		primaryStage.getIcons().add(
				   new Image(Aurous.class.getResourceAsStream("/resources/logo.png"))); 

	}

	public static void main(final String[] args) {
		launch(args);
	}
}
