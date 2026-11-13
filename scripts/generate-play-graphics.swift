import AppKit
import Foundation

// Google Play feature graphic (1024×500, no transparency) and 512×512 icon, drawn natively.
// Usage: swift scripts/generate-play-graphics.swift <app icon 1024 png> <output dir>
let args = CommandLine.arguments
let icon = NSImage(contentsOfFile: args[1])!
let out = URL(fileURLWithPath: args[2])

func render(_ width: Int, _ height: Int, _ draw: () -> Void) -> Data {
    let cg = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4,
                       space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
    NSGraphicsContext.saveGraphicsState()
    NSGraphicsContext.current = NSGraphicsContext(cgContext: cg, flipped: false)
    draw()
    NSGraphicsContext.restoreGraphicsState()
    return NSBitmapImageRep(cgImage: cg.makeImage()!).representation(using: .png, properties: [:])!
}
func color(_ hex: Int, _ alpha: CGFloat = 1) -> NSColor {
    NSColor(srgbRed: CGFloat(hex >> 16 & 255) / 255, green: CGFloat(hex >> 8 & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: alpha)
}
func text(_ s: String, _ size: CGFloat, _ weight: NSFont.Weight, _ c: NSColor, at p: NSPoint) {
    NSAttributedString(string: s, attributes: [.font: NSFont.systemFont(ofSize: size, weight: weight), .foregroundColor: c]).draw(at: p)
}
func card(_ rect: NSRect, _ fill: NSColor, radius: CGFloat = 28) {
    fill.setFill(); NSBezierPath(roundedRect: rect, xRadius: radius, yRadius: radius).fill()
}

let feature = render(1024, 500) {
    NSGradient(starting: color(0x1E5A43), ending: color(0x2F7D5C))!.draw(in: NSRect(x: 0, y: 0, width: 1024, height: 500), angle: 35)
    color(0xFFFFFF, 0.07).setFill()
    NSBezierPath(ovalIn: NSRect(x: 640, y: 180, width: 520, height: 520)).fill()
    NSBezierPath(ovalIn: NSRect(x: 760, y: -160, width: 360, height: 360)).fill()
    // Brand
    let mark = NSRect(x: 64, y: 300, width: 112, height: 112)
    NSGraphicsContext.saveGraphicsState()
    NSBezierPath(roundedRect: mark, xRadius: 26, yRadius: 26).addClip()
    icon.draw(in: mark)
    NSGraphicsContext.restoreGraphicsState()
    text("Climate Widget", 58, .bold, .white, at: NSPoint(x: 64, y: 196))
    text("가전 남은 시간과 집 공기를 한눈에", 30, .semibold, color(0xFFFFFF, 0.88), at: NSPoint(x: 66, y: 146))
    text("광고 없음 · 데이터 수집 없음 · 읽기 전용", 21, .medium, color(0xFFFFFF, 0.7), at: NSPoint(x: 66, y: 92))
    // A countdown card and an air card, like the app.
    card(NSRect(x: 600, y: 268, width: 360, height: 136), color(0xFAF8F2))
    text("체험용 세탁기", 24, .bold, color(0x1B2420), at: NSPoint(x: 630, y: 352))
    text("헹굼 중", 18, .semibold, color(0x24664D), at: NSPoint(x: 630, y: 322))
    text("23:41", 46, .bold, color(0x1B2420), at: NSPoint(x: 820, y: 330))
    card(NSRect(x: 630, y: 290, width: 300, height: 10), color(0xE3E1D8), radius: 5)
    card(NSRect(x: 630, y: 290, width: 150, height: 10), color(0x24664D), radius: 5)
    card(NSRect(x: 600, y: 96, width: 360, height: 148), color(0xFFFFFF))
    text("습도", 18, .medium, color(0x6B7570), at: NSPoint(x: 630, y: 196))
    text("47%", 50, .bold, color(0x24664D), at: NSPoint(x: 628, y: 130))
    text("온도", 18, .medium, color(0x6B7570), at: NSPoint(x: 800, y: 196))
    text("23.4°", 50, .bold, color(0x1B2420), at: NSPoint(x: 798, y: 130))
    card(NSRect(x: 630, y: 114, width: 300, height: 10), color(0xE3E1D8), radius: 5)
    card(NSRect(x: 730, y: 114, width: 100, height: 10), color(0x24664D, 0.35), radius: 5)
}
try! feature.write(to: out.appendingPathComponent("feature/1-feature.png"))
let small = render(512, 512) { icon.draw(in: NSRect(x: 0, y: 0, width: 512, height: 512)) }
try! small.write(to: out.appendingPathComponent("icon/1-icon.png"))
print("Wrote feature graphic and icon")
