import XCTest

final class DemoModeUITests: XCTestCase {
    @MainActor func testDemoStartsWithoutAccountAndCanBeEnded() {
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["startDemo"]
        XCTAssertTrue(start.waitForExistence(timeout: 15))
        attach(app, "1-first-launch")
        start.tap()
        let notice = app.staticTexts["demoNotice"]
        XCTAssertTrue(notice.waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["체험용 가습기"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["체험용 공기청정기"].exists)
        XCTAssertTrue(start.waitForNonExistence(timeout: 5))
        attach(app, "2-home")

        for _ in 0..<4 where !app.buttons["tileSmall"].isHittable { app.swipeUp() }
        attach(app, "3-home-bottom")
        app.buttons["tileSmall"].tap()
        let close = app.buttons["closeGuide"]
        XCTAssertTrue(close.waitForExistence(timeout: 5))
        attach(app, "4-widget-guide")
        app.swipeUp()
        attach(app, "5-widget-palettes")
        app.swipeDown()
        close.tap()
        XCTAssertTrue(close.waitForNonExistence(timeout: 5))

        // The settings sheet must open both before and after a relaunch.
        let settings = app.buttons["openSettings"]
        for _ in 0..<4 where !settings.isHittable { app.swipeDown() }
        settings.tap()
        XCTAssertTrue(app.buttons["disconnect"].waitForExistence(timeout: 5), "Settings should open")
        attach(app, "6-settings")
        app.buttons["closeSettings"].tap()
        XCTAssertTrue(app.buttons["disconnect"].waitForNonExistence(timeout: 5))

        app.terminate()
        app.launch()
        XCTAssertTrue(notice.waitForExistence(timeout: 10), "Demo should survive relaunch")
        XCTAssertFalse(start.exists)

        settings.tap()
        let end = app.buttons["disconnect"]
        XCTAssertTrue(end.waitForExistence(timeout: 5))
        for _ in 0..<6 where !end.isHittable { app.swipeUp() }
        end.tap()
        // Ending the demo returns to first-run onboarding with the sample option available again.
        for _ in 0..<6 where !start.isHittable { app.swipeDown() }
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        XCTAssertFalse(notice.exists)
    }
    @MainActor private func attach(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
