//===----------------------------------------------------------------------===//
//
// This source file is part of the Swift.org open source project
//
// Copyright (c) 2025 Apple Inc. and the Swift project authors
// Licensed under Apache License v2.0 with Runtime Library Exception
//
// See https://swift.org/LICENSE.txt for license information
// See https://swift.org/CONTRIBUTORS.txt for the list of Swift project authors
//
//===----------------------------------------------------------------------===//

/// Represents the current weather conditions for a specific location.
public struct WeatherData: Hashable {
    public let temperature: Double
    public let windSpeed: Double
    public let windDirection: Double
}

#if canImport(FoundationEssentials)
public import FoundationEssentials
#else
public import Foundation
#endif

#if os(Android)
public typealias Amount = Double
#else
public typealias Amount = Decimal
#endif

public struct TypeAliasProblem: Hashable {
    public var myAmount: Amount
}
