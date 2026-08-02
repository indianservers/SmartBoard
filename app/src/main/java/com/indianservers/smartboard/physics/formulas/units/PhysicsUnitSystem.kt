package com.indianservers.smartboard.physics.formulas.units

enum class PhysicsUnitDimension { Length, Time, Mass, Velocity, Acceleration, Force, Energy, Power, Pressure, Charge, Current, Voltage, Resistance, Capacitance, MagneticField, Frequency, Temperature, Angle, Momentum, Torque, Dimensionless }

data class PhysicsUnit(val symbol: String, val name: String, val dimension: PhysicsUnitDimension, val scaleToSi: Double = 1.0, val offsetToSi: Double = 0.0) {
    fun toSi(value: Double): Double = value * scaleToSi + offsetToSi
    fun fromSi(value: Double): Double = (value - offsetToSi) / scaleToSi
}

object PhysicsUnitSystem {
    val units = listOf(
        PhysicsUnit("m", "metre", PhysicsUnitDimension.Length), PhysicsUnit("cm", "centimetre", PhysicsUnitDimension.Length, .01), PhysicsUnit("km", "kilometre", PhysicsUnitDimension.Length, 1000.0),
        PhysicsUnit("s", "second", PhysicsUnitDimension.Time), PhysicsUnit("min", "minute", PhysicsUnitDimension.Time, 60.0), PhysicsUnit("h", "hour", PhysicsUnitDimension.Time, 3600.0),
        PhysicsUnit("kg", "kilogram", PhysicsUnitDimension.Mass), PhysicsUnit("g", "gram", PhysicsUnitDimension.Mass, .001),
        PhysicsUnit("m/s", "metre per second", PhysicsUnitDimension.Velocity), PhysicsUnit("km/h", "kilometre per hour", PhysicsUnitDimension.Velocity, 1.0 / 3.6),
        PhysicsUnit("m/s²", "metre per second squared", PhysicsUnitDimension.Acceleration), PhysicsUnit("N", "newton", PhysicsUnitDimension.Force),
        PhysicsUnit("J", "joule", PhysicsUnitDimension.Energy), PhysicsUnit("W", "watt", PhysicsUnitDimension.Power), PhysicsUnit("Pa", "pascal", PhysicsUnitDimension.Pressure),
        PhysicsUnit("C", "coulomb", PhysicsUnitDimension.Charge), PhysicsUnit("A", "ampere", PhysicsUnitDimension.Current), PhysicsUnit("V", "volt", PhysicsUnitDimension.Voltage),
        PhysicsUnit("Ω", "ohm", PhysicsUnitDimension.Resistance), PhysicsUnit("F", "farad", PhysicsUnitDimension.Capacitance), PhysicsUnit("T", "tesla", PhysicsUnitDimension.MagneticField),
        PhysicsUnit("Hz", "hertz", PhysicsUnitDimension.Frequency), PhysicsUnit("K", "kelvin", PhysicsUnitDimension.Temperature), PhysicsUnit("°C", "degree Celsius", PhysicsUnitDimension.Temperature, 1.0, 273.15),
        PhysicsUnit("rad", "radian", PhysicsUnitDimension.Angle), PhysicsUnit("kg·m/s", "kilogram metre per second", PhysicsUnitDimension.Momentum), PhysicsUnit("N·m", "newton metre", PhysicsUnitDimension.Torque),
    )
    private val bySymbol = units.associateBy { it.symbol }
    fun convert(value: Double, from: String, to: String): Double {
        require(value.isFinite()) { "Enter a finite value." }
        val source = requireNotNull(bySymbol[from]) { "Unknown unit: $from" }; val target = requireNotNull(bySymbol[to]) { "Unknown unit: $to" }
        require(source.dimension == target.dimension) { "Units must measure the same physical dimension." }
        val result = target.fromSi(source.toSi(value)); require(result.isFinite()) { "Conversion produced an invalid result." }; return result
    }
}

