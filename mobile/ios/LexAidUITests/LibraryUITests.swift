import XCTest

final class LibraryUITests: XCTestCase {
    @MainActor
    func testOpensLearningSite() {
        let app = XCUIApplication()
        app.launch()
        let web = app.webViews.firstMatch
        XCTAssertTrue(web.waitForExistence(timeout: 30))
        let login = web.textFields.firstMatch
        let dashboard = web.staticTexts["资料检索"].firstMatch
        let rendered = NSPredicate { _, _ in login.exists || dashboard.exists }
        let wait = XCTNSPredicateExpectation(predicate: rendered, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [wait], timeout: 50), .completed)
    }

    @MainActor
    func testSignedInLibrarySurvivesRelaunch() throws {
        let environment = ProcessInfo.processInfo.environment
        let username = try XCTUnwrapOrSkip(environment["LEXAID_QA_USERNAME"])
        let password = try XCTUnwrapOrSkip(environment["LEXAID_QA_PASSWORD"])
        let app = XCUIApplication()
        app.launch()
        let web = app.webViews.firstMatch
        let usernameField = web.textFields.firstMatch
        if usernameField.waitForExistence(timeout: 15) {
            usernameField.tap()
            usernameField.typeText(username)
            let passwordField = web.secureTextFields.firstMatch
            passwordField.tap()
            passwordField.typeText(password + "\n")
        }
        let library = web.staticTexts["资料检索"].firstMatch
        XCTAssertTrue(library.waitForExistence(timeout: 45))
        let provisions = web.links.containing(.staticText, identifier: "法条检索").firstMatch
        let target = provisions.exists ? provisions : web.links["法条检索"].firstMatch
        XCTAssertTrue(target.exists)
        target.tap()
        XCTAssertTrue(web.textFields.firstMatch.waitForExistence(timeout: 20))
        app.terminate()
        app.launch()
        XCTAssertTrue(library.waitForExistence(timeout: 45))
    }

    private func XCTUnwrapOrSkip(_ value: String?) throws -> String {
        guard let value, !value.isEmpty else { throw XCTSkip("需要专用测试账号。") }
        return value
    }
}
