import XCTest

final class DemoModeUITests: XCTestCase {
    @MainActor func testDemoStartsWithoutAccountAndCanBeEnded() {
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["startDemo"]
        XCTAssertTrue(start.waitForExistence(timeout: 15))
        attach(app, "1-first-launch")
        start.tap()
        XCTAssertTrue(app.staticTexts["demoNotice"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["체험용 가습기"].exists || app.staticTexts["체험용 가습기".uppercased()].exists)
        XCTAssertFalse(start.exists)
        attach(app, "2-demo-active")

        app.terminate()
        app.launch()
        XCTAssertTrue(app.staticTexts["demoNotice"].waitForExistence(timeout: 10), "Demo should survive relaunch")

        let end = app.buttons["disconnect"]
        for _ in 0..<6 where !end.isHittable { app.swipeUp() }
        end.tap()
        for _ in 0..<6 where !start.exists { app.swipeDown() }
        XCTAssertTrue(start.waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["demoNotice"].exists)
    }
    @MainActor private func attach(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
