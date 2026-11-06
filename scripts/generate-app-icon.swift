import AppKit
import Foundation

// Native rendition of the existing climate droplet mark; iOS applies its own icon mask.
let size = 1024
let cg = CGContext(data: nil, width: size, height: size, bitsPerComponent: 8,
    bytesPerRow: size * 4, space: CGColorSpaceCreateDeviceRGB(),
    bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
let context = NSGraphicsContext(cgContext: cg, flipped: false)
NSGraphicsContext.saveGraphicsState()
NSGraphicsContext.current = context
NSColor(srgbRed: 250/255, green: 248/255, blue: 242/255, alpha: 1).setFill()
NSBezierPath(rect: NSRect(x: 0, y: 0, width: size, height: size)).fill()
let drop = NSBezierPath()
drop.move(to: NSPoint(x: 512, y: 844))
drop.curve(to: NSPoint(x: 264, y: 384), controlPoint1: NSPoint(x: 431, y: 700), controlPoint2: NSPoint(x: 264, y: 528))
drop.curve(to: NSPoint(x: 760, y: 384), controlPoint1: NSPoint(x: 264, y: 56), controlPoint2: NSPoint(x: 760, y: 56))
drop.curve(to: NSPoint(x: 512, y: 844), controlPoint1: NSPoint(x: 760, y: 528), controlPoint2: NSPoint(x: 590, y: 705))
drop.close()
NSColor(srgbRed: 36/255, green: 102/255, blue: 77/255, alpha: 1).setFill()
drop.fill()
NSColor(srgbRed: 220/255, green: 238/255, blue: 213/255, alpha: 1).setStroke()
for y: CGFloat in [343, 431] {
    let line = NSBezierPath()
    line.move(to: NSPoint(x: 374, y: y))
    line.curve(to: NSPoint(x: 650, y: y), controlPoint1: NSPoint(x: 460, y: y-35), controlPoint2: NSPoint(x: 564, y: y+35))
    line.lineWidth = 36; line.lineCapStyle = .round
    line.stroke()
}
NSColor(srgbRed: 225/255, green: 173/255, blue: 101/255, alpha: 1).setFill()
NSBezierPath(ovalIn: NSRect(x: 660, y: 666, width: 146, height: 146)).fill()
NSGraphicsContext.restoreGraphicsState()
let directory = URL(fileURLWithPath: "ios/App/Assets.xcassets/AppIcon.appiconset", isDirectory: true)
try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
let bitmap = NSBitmapImageRep(cgImage: cg.makeImage()!)
try bitmap.representation(using: .png, properties: [:])!.write(to: directory.appendingPathComponent("AppIcon.png"))
let contents: [String: Any] = ["images": [["filename": "AppIcon.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"]], "info": ["author": "xcode", "version": 1]]
try JSONSerialization.data(withJSONObject: contents, options: [.prettyPrinted, .sortedKeys]).write(to: directory.appendingPathComponent("Contents.json"))
print("Generated opaque 1024px iOS app icon")
