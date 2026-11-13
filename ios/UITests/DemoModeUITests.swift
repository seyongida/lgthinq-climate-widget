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
        // The sample washer is always mid-cycle and the sample dryer has finished.
        XCTAssertTrue(app.descendants(matching: .any)["cycle"].firstMatch.waitForExistence(timeout: 5), "A running appliance should be listed")
        XCTAssertTrue(app.staticTexts["건조가 끝났어요"].exists || app.descendants(matching: .any)["alerts"].exists, "The finished dryer should ask for attention")
        app.swipeUp()
        attach(app, "3-home-middle")
        for _ in 0..<3 where !app.descendants(matching: .any)["energy"].firstMatch.exists { app.swipeUp() }
        XCTAssertTrue(app.descendants(matching: .any)["energy"].firstMatch.waitForExistence(timeout: 10), "Sample energy use should appear")
        attach(app, "3-home-energy")
        for _ in 0..<5 where !app.buttons["tileSmall"].isHittable { app.swipeUp() }
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
    /// Manual QA only: tours an app already launched with a real account. Skipped unless REAL_TOUR is set.
    @MainActor func testRealAccountTour() throws {
        guard ProcessInfo.processInfo.environment["REAL_TOUR"] == "1" else { throw XCTSkip("Real account tour is manual") }
        let app = XCUIApplication()
        app.activate()
        XCTAssertTrue(app.buttons["openSettings"].waitForExistence(timeout: 10))
        for index in 0..<7 {
            attach(app, "8-real-\(index)")
            app.swipeUp()
        }
    }
    /// Manual QA only: adds the climate widget to the Home Screen and captures it. Skipped unless WIDGET_TOUR is set.
    @MainActor func testWidgetOnHomeScreen() throws {
        guard ProcessInfo.processInfo.environment["WIDGET_TOUR"] == "1" else { throw XCTSkip("Widget tour is manual") }
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["startDemo"]
        if start.waitForExistence(timeout: 10) { start.tap() }
        sleep(3)
        XCUIDevice.shared.press(.home)
        let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")
        sleep(2)
        springboard.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.55)).press(forDuration: 2)
        sleep(2)
        shot(springboard, "9-edit-mode")
        let edit = springboard.buttons["편집"]
        if edit.waitForExistence(timeout: 3) { edit.tap(); sleep(1) }
        let addWidget = springboard.buttons["위젯 추가"]
        XCTAssertTrue(addWidget.waitForExistence(timeout: 5), "Add widget button")
        addWidget.tap()
        sleep(2)
        let search = springboard.searchFields.firstMatch
        if search.waitForExistence(timeout: 5) { search.tap(); search.typeText("Climate"); sleep(2) }
        shot(springboard, "9-gallery")
        let cell = springboard.cells.containing(.staticText, identifier: "Climate Widget").firstMatch
        if cell.waitForExistence(timeout: 5) { cell.tap() } else { springboard.staticTexts["Climate Widget"].firstMatch.tap() }
        sleep(2)
        shot(springboard, "9-sizes")
        let add = springboard.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS '위젯 추가'")).firstMatch
        XCTAssertTrue(add.waitForExistence(timeout: 5))
        add.tap()
        sleep(2)
        let done = springboard.buttons["완료"]
        if done.waitForExistence(timeout: 3) { done.tap() }
        sleep(4)
        shot(springboard, "9-home-widget")
    }
    /// App Store screenshots from the sample home. Skipped unless STORE_SHOTS is set; scripts/store-screenshots.sh runs it.
    @MainActor func testStoreScreenshots() throws {
        guard ProcessInfo.processInfo.environment["STORE_SHOTS"] == "1" else { throw XCTSkip("Store screenshots are manual") }
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["startDemo"]
        XCTAssertTrue(start.waitForExistence(timeout: 15))
        shot(app, "store-5-welcome")
        start.tap()
        XCTAssertTrue(app.descendants(matching: .any)["cycle"].firstMatch.waitForExistence(timeout: 10))
        sleep(2)
        shot(app, "store-1-home")
        for _ in 0..<4 where !app.descendants(matching: .any)["energy"].firstMatch.isHittable { app.swipeUp() }
        sleep(1)
        shot(app, "store-3-energy")
        for _ in 0..<6 where !app.buttons["tileSmall"].isHittable { app.swipeUp() }
        app.buttons["tileSmall"].tap()
        let close = app.buttons["closeGuide"]
        XCTAssertTrue(close.waitForExistence(timeout: 5))
        sleep(1)
        shot(app, "store-4-widgets")
        close.tap()
        XCTAssertTrue(close.waitForNonExistence(timeout: 5))
        for _ in 0..<6 { app.swipeDown() }
        // Live Activities exist on iPhone only.
        let device = XCUIDevice.shared
        let lock = NSSelectorFromString("pressLockButton")
        if UIDevice.current.userInterfaceIdiom == .phone, device.responds(to: lock) {
            device.perform(lock)
            sleep(2)
            device.press(.home)
            sleep(2)
            let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["허용"]
            if allow.waitForExistence(timeout: 3) { allow.tap() }
            sleep(3)
            shot(app, "store-2-lock-screen")
            device.press(.home); sleep(1); device.press(.home)
            app.activate()
        }
        app.buttons["openSettings"].tap()
        let end = app.buttons["disconnect"]
        XCTAssertTrue(end.waitForExistence(timeout: 5))
        for _ in 0..<6 where !end.isHittable { app.swipeUp() }
        end.tap()
    }
    @MainActor private func shot(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        attachment.name = name; attachment.lifetime = .keepAlways; add(attachment)
    }
    /// Shows the Lock Screen so the Live Activity can be inspected. Uses the simulator's lock button.
    @MainActor func testLiveActivityOnLockScreen() throws {
        let app = XCUIApplication()
        app.launch()
        let start = app.buttons["startDemo"]
        if start.waitForExistence(timeout: 10) { start.tap() }
        XCTAssertTrue(app.descendants(matching: .any)["cycle"].firstMatch.waitForExistence(timeout: 10))
        let device = XCUIDevice.shared
        let lock = NSSelectorFromString("pressLockButton")
        guard device.responds(to: lock) else { throw XCTSkip("Lock button is not available") }
        device.perform(lock)
        sleep(2)
        device.press(.home)
        sleep(2)
        // First use asks whether to allow Live Activities from this app.
        let allow = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["허용"]
        if allow.waitForExistence(timeout: 3) { allow.tap() }
        sleep(3)
        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        shot.name = "7-lock-screen"; shot.lifetime = .keepAlways; add(shot)
        // Leave the simulator unlocked and the sample cleared for the next run.
        device.press(.home); sleep(1); device.press(.home)
        app.activate()
        app.buttons["openSettings"].tap()
        let end = app.buttons["disconnect"]
        XCTAssertTrue(end.waitForExistence(timeout: 5))
        for _ in 0..<6 where !end.isHittable { app.swipeUp() }
        end.tap()
    }
    @MainActor private func attach(_ app: XCUIApplication, _ name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
