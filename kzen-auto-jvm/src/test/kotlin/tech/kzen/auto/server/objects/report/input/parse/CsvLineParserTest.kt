package tech.kzen.auto.server.objects.report.input.parse

import org.junit.Test
import kotlin.test.assertEquals


class CsvLineParserTest {
    //-----------------------------------------------------------------------------------------------------------------
    @Test
    fun simpleBareLine() {
        val csvLine = "id,url,region,region_url,price,year,manufacturer,model,condition,cylinders,fuel,odometer," +
                "title_status,transmission,vin,drive,size,type,paint_color,image_url,description,county,state," +
                "lat,long"

        val cells = csvLine.split(",")

        assertEquals(cells, FastCsvLineParser.parseLine(csvLine).toList())
    }


    @Test
    fun utf8Line() {
        val csvLine = """7088746062,https://greensboro.craigslist.org/ctd/d/cary-2012-acura-tl-base-4dr-sedan/7088746062.html,greensboro,https://greensboro.craigslist.org,10299,2012,acura,tl,,,gas,90186,clean,automatic,19UUA8F22CA003926,,,other,blue,https://images.craigslist.org/01414_3LIXs9EO33z_600x450.jpg,"2012 Acura TL Base 4dr Sedan     Offered by: Best Import Auto Sales Inc — (919) 800-0650 — ${'$'}10,299     PRISTINE CONDITION INSIDE AND OUT   Best Import Auto Sales Inc    Year: 2012 Make: Acura Model: TL Series: Base 4dr Sedan VIN: 19UUA8F22CA003926  Condition: Used Mileage: 90,186  Exterior: Blue Interior: Black Body: Sedan Transmission: Automatic 6-Speed Engine: 3.5L V6      **** Best Import Auto Sales Inc. 🚘 Raleigh Auto Dealer *****  ⚡️⚡️⚡️ Call Or Text (919) 800-0650 ⚡️⚡️⚡️  ✅ - We can arrange Financing Options with most banks and credit unions!!!!     ✅ Extended Warranties Available on most vehicles!! ""Call To Inquire""  ✅ Full Service ASE-Certified Shop Onsite!       More vehicle details: best-import-auto-sales-inc.hammerwebsites.net/v/cfoamRwq     Address: 1501 Buck Jones Rd Raleigh, NC 27606   Phone: (919) 800-0650     Website: www.bestimportsonline.com      📲 ☎️ Call or text (919) 800-0650 for quick answers to your questions about this Acura TL Your message will always be answered by a real human — never an automated system.     Disclaimer: Best Import Auto Sales Inc will never sell, share, or spam your mobile number. Standard text messaging rates may apply.       2012 Acura TL Base 4dr Sedan   30b9c4702111452eb57503c99e795660",,nc,35.7636,-78.7443"""
        assertEquals(25, FastCsvLineParser.parseLine(csvLine).toList().size)
        assertEquals(csvLine, FastCsvLineParser.parseLine(csvLine).toCsv())
    }


    @Test
    fun utf8Lines() {
        val csvLineA = """7088746062,https://greensboro.craigslist.org/ctd/d/cary-2012-acura-tl-base-4dr-sedan/7088746062.html,greensboro,https://greensboro.craigslist.org,10299,2012,acura,tl,,,gas,90186,clean,automatic,19UUA8F22CA003926,,,other,blue,https://images.craigslist.org/01414_3LIXs9EO33z_600x450.jpg,"2012 Acura TL Base 4dr Sedan     Offered by: Best Import Auto Sales Inc — (919) 800-0650 — ${'$'}10,299     PRISTINE CONDITION INSIDE AND OUT   Best Import Auto Sales Inc    Year: 2012 Make: Acura Model: TL Series: Base 4dr Sedan VIN: 19UUA8F22CA003926  Condition: Used Mileage: 90,186  Exterior: Blue Interior: Black Body: Sedan Transmission: Automatic 6-Speed Engine: 3.5L V6      **** Best Import Auto Sales Inc. 🚘 Raleigh Auto Dealer *****  ⚡️⚡️⚡️ Call Or Text (919) 800-0650 ⚡️⚡️⚡️  ✅ - We can arrange Financing Options with most banks and credit unions!!!!     ✅ Extended Warranties Available on most vehicles!! ""Call To Inquire""  ✅ Full Service ASE-Certified Shop Onsite!       More vehicle details: best-import-auto-sales-inc.hammerwebsites.net/v/cfoamRwq     Address: 1501 Buck Jones Rd Raleigh, NC 27606   Phone: (919) 800-0650     Website: www.bestimportsonline.com      📲 ☎️ Call or text (919) 800-0650 for quick answers to your questions about this Acura TL Your message will always be answered by a real human — never an automated system.     Disclaimer: Best Import Auto Sales Inc will never sell, share, or spam your mobile number. Standard text messaging rates may apply.       2012 Acura TL Base 4dr Sedan   30b9c4702111452eb57503c99e795660",,nc,35.7636,-78.7443"""
        val csvLineB = """7088745301,https://greensboro.craigslist.org/ctd/d/bmw-3-series-335-convertible-harmon/7088745301.html,greensboro,https://greensboro.craigslist.org,0,2011,bmw,335,,6 cylinders,gas,115120,clean,automatic,,rwd,,convertible,blue,https://images.craigslist.org/00S0S_1kTatLGLxB5_600x450.jpg,"BMW 3 Series 335i Convertible Navigation Dakota Leather Heated Seats Automatic High Beam We Finance!Price: Call for PricingCall Today    888-728-7443You can Fill out a Free Super Quick Pre-Approval Credit Application here!For Instant Online Approvals!  OPEN TODAY!   888-768-8164Copy The Link Belowhttps://www.smartchevrolet.com/finance/apply-for-financing/Finance Online, We Can Ship To Your Door!We Have First Time Buyers Program!We Use Over 40 banks a Credit Unions With Lowest Rates Possible For All Types Of Credit!FINANCING FOR ALL TYPES OF CREDIT! BAD CREDIT, NO CREDIT, Repossession NO PROBLEM!SLOW PAYMENTS, BANKRUPTCY, REPOS NO PROBLEM!COLLECTIONS, JUDGEMENTS, DIVORCE NO PROBLEM!TRADE-INS Great! WE BUY CARS Everyday, Even if you don't buy Ours!Se Habla EspanolAsk for: Craigslist Salesstore: (888) 728-7443VIN Number: WBADX7C51BE579063Engine: 3.0L 6-Cylinder DOHC Twin TurbochargedStock No: 579063KBWarranty: Original Manufacturer WarrantyMiles: 115120Interior: Oyster BlackTrans: 6-Speed Automatic SteptronicExterior: Blue Water MetallicOptions:Navigation SystemReal Time Traffic InformationCold Weather PackageConvenience PackagePremium PackageConvertible Hardtop8 SpeakersAM/FM CD/MP3 RadioAM/FM RadioCD PlayerIpod & USB AdapterMP3 DecoderRadio Data SystemSIRIUS Satellite RadioAir ConditioningAutomatic Temperature ControlFront Dual Zone A/CRear Air ConditioningRear Window DefrosterComfort Access Keyless EntryLumbar SupportMemory SeatPower Driver SeatPower SteeringPower WindowsRemote Keyless EntrySteering Wheel Mounted A/C ControlsSteering Wheel Mounted Audio ControlsVoice CommandActive SteeringFour Wheel Independent SuspensionSpeed-Sensing SteeringSport SuspensionTraction Control4-Wheel Disc BrakesABS BrakesAnti-Whiplash Front Head RestraintsDual Front Impact AirbagsDual Front Side Impact AirbagsFront Anti-Roll BarIntegrated Roll-Over ProtectionKnee AirbagLow Tire Pressure WarningOccupant Sensing AirbagRear Anti-Roll BarBMW Assist W/Bluetooth®Smartphone IntegrationBrake AssistElectronic Stability ControlAutomatic High BeamsDelay-Off HeadlightsFront Fog LightsFully Automatic HeadlightsHigh Intensity Discharge Headlights: Bi-XenonRetractable Headlight WashersAnti-Theft Alarm SystemActive Cruise ControlSpeed ControlAuto-Dimming MirrorsBumpers: Body-ColorHeated Door MirrorsPower Door MirrorsAuto-Dimming Rear-View MirrorBamboo Anthracite Wood TrimConvertible Roof LiningDigital Compass MirrorDriver Door BinDriver Vanity MirrorFront Reading LightsGenuine Wood Console InsertGenuine Wood Dashboard InsertGenuine Wood Door Panel InsertHeated Steering WheelIlluminated EntryLeather Shift KnobOutside Temperature DisplayPark Distance ControlPassenger Vanity MirrorPower Convertible RoofRear Reading LightsRear Seat Center ArmrestSmartphone Integration (DISC)Sport Steering WheelTachometerTelescoping Steering WheelTilt Steering WheelTrip ComputerUniversal Garage-Door OpenerDakota Leather UpholsteryFront Bucket SeatsFront Center ArmrestHeated Front SeatsPower Passenger SeatThrough-Loading System W/Integrated Transport BagPassenger Door Bin17"" Light Alloy Star-Spoke (Style 339) WheelsGlass Rear WindowRain Sensing WipersVariably Intermittent WipersCARFAX CERTIFIEDDescription:BMW 3 Series 335i Convertible, Navigation System, Dakota Leather, Heated Seats, Automatic High Beams, Carfax Cerified, Premium Package, Steptronic Auto Trans. We Finance! ${'$'}11040 In Installed Options on the car!INSTALLED OPTIONS[205] Steptronic Automatic Trans.normalsport & manual shift modes${'$'}1,375[896] Blue Water Metallic	${'$'}550[LCCX] Oyster/Black Dakota Leather	${'$'}0[ZPP] Premium PackageUniversal garage-door openerAuto-dimming mirrorsAuto-dimming rearview mirrorLumbar supportInterior mirror with compassBMW Assist with Bluetooth${'$'}1,650[ZCV] Convenience PackageAlarm SystemComfort Access keyless entryPark Distance Control${'$'}1,250[217] Active Steering	${'$'}1,550[5AC] Automatic High Beams	${'$'}250[655] Sirius XM Radio W/ 1 Year Sub.(1) year subscription${'$'}350[6FL] I Pod And Usb Adapter	${'$'}400[494] Heated Front Seats	${'$'}500[248] Heated Steering Wheel	${'$'}190[609] Navigation System16:9 high-resolution display3-D screenvoice command systemreal time traffic infoiDrive system w/on-board computer6 programmable memory buttons12 GB media storage${'$'}2,100[4BY] Bamboo Anthracite Wood Trim	${'$'}0Original Shipping Charge	${'$'}875RETAIL PRICE (ORIGINALLY NEW)	${'$'}62,240.00You can Fill out a Free Super Quick Pre-Approval Credit Application here!https://www.smartchevrolet.com/finance/apply-for-financing/We Use Over 40 banks a Credit Unions For the Lowest Rates Possible For All Types Of Credit!FINANCING FOR ALL TYPES OF CREDIT!BAD CREDIT, NO CREDIT, Repossession NO PROBLEM!SLOW PAYMENTS, BANKRUPTCY, REPOS NO PROBLEM!COLLECTIONS, JUDGEMENTS, DIVORCE NO PROBLEM!MONTHLY PAYMENTS TO FIT ANY INCOME!335, 325, 328, 335, 330, 550, 528, 535, 525, 5 Series, 525i, 528i, 528e, 530i, 535i, 540i, 540, 545, 545i, 550i, 650, 750, 760, M3, M5, M6, X1, X3, X6, X5, Z4, X5 M, X6 M, 550 Gran Turismo, 535 Gran Turismo, ActiveHybrid X6, 740, Alpina B7, ActiveHybrid 750, 1 Series M, 640, ActiveHybrid 5, 320, 640 Gran Coupe, X1, 650 Gran Coupe, ActiveHybrid 3, ActiveHybrid 740, 228, 428, M6 Gran Coupe, 328 Gran Turismo, 335 Gran Turismo, 435, 535d, 328d, i3, i8, ActiveHybrid 7, M235, M4, X4, 435 Gran Coupe, 428 Coupe, 325 , 325i , 330 , 330i , 328 , 328i , 335 , 335i, 525 , 745i , 745li , 530i , 545 , 550 , 645 , 650, X5 , 750li , 750 , 750i , X3, X1, X6, 128i, 135i 2005 2006 2007 2008 2009 2010 2011 2012 2013 2014 2015 2016 2017 BMW, 0 Down Payment, Suv, 4x4, Buy Here Pay Here, On Lot Financing, Owner Finance, 500 down Bad Credit, Bluetooth, Cheap for cheap, Cars owner Financing, Cheap down Payment, no money down, cars low down payment, car lots No Money Down Bad Credit, used cars for sale, Used SUV, bad credit, ${'$'}1000 or less used cars, For Sale By Owner, Will Trade for Motorcycles, For Sale Near Me, for sale craigslist, low mileage, low miles, very low miles, 500 down no credit check, ${'$'}500, Low Down Payment, No CreditA27FBAFAEA464DBBB650D168074EE06C 28003645 8284589BMW 335 335i",,nc,,"""
        val lines = "$csvLineA\r\n$csvLineB\r\n"
        val parsed = FastCsvLineParser.parseLines(lines)
        assertEquals(2, parsed.size)
        assertEquals(csvLineA, parsed[0].toCsv())
        assertEquals(csvLineB, parsed[1].toCsv())
    }


    @Test
    fun multilineRecord() {
        val csvRecord = """7088697962,https://greensboro.craigslist.org/ctd/d/rural-hall-2016-ram-2500-laramie-crew/7088697962.html,greensboro,https://greensboro.craigslist.org,38900,2016,ram,2500,,,diesel,99801,clean,automatic,3C6UR5KL0GG160114,4wd,,pickup,black,https://images.craigslist.org/00W0W_dX4bzYXJNmG_600x450.jpg,"2016 RAM 2500 Laramie Crew Cab LWB 4WD     Offered by: Diesels, Lifted 4x4s, Crewcabs - Call or Text (336) 203-9408 — (336) 203-9408 — ${'$'}38,900      We pride ourselves on doing our very best to give our customers the best buying experience possible. We also hand pick our trucks and suvs from a rust free environment. So 1-2 years down the road you're not wishing you'd never seen that rusty truck. Something we strive to avoid. We pay more for the non-rusty vehicles so your not stuck with costly rust repairs. Not only the repairs you can see but the ones you can't. which include things like brakes exhaust frame rot bed support rot and the one thing that cost you more in the long run the things that nobody really hears about.... its when you take your truck in for a repair of any kind and the mechanic has to spend hours fighting the rusty bolts and brackets that have fused themselves together. This will definitely cost you money. Why you ask?? Because time and more importantly mechanic frustration cost you money!! Ask us how we can find you the right truck that's going to last.Visit Premier Trucks and Imports online at premiertrucks.net to see more pictures of this vehicle or call us at 366-399-3221 today to schedule your test drive. WE HAVE SEVERAL CREDIT UNIONS THAT WANT YOUR BUSINESS LOW RATES AND GREAT TERMSFEEL FREE TO CALL OR TEXT ANYTIME! WARRANTIES AVAILABLE ON ALMOST ALL VEHICLES WE SELL!!!   Diesels, Lifted 4x4s, Crewcabs - Call or Text (336) 203-9408    Year: 2016 Make: RAM Model: 2500 Series: Laramie Crew Cab LWB 4WD VIN: 3C6UR5KL0GG160114 Stock #: 60114 Condition: Used Mileage: 99,801  Exterior: Black Interior: Black Body: Truck Transmission: Automatic Engine: 6.7L L6 OHV 24V TURBO DIESEL Drivetrain: 4WD     🏁🥇🏁🥇 PREMIER TRUCKS AND IMPORTS 🥇🏁🥇 🏁  🔹🔷 📲 ☎️️ CALL OR TEXT US (336) 203-9408 📲 ☎️️ 🔷🔹  ✅ QUALITY VEHICLES - LOW PRICES - WORLD CLASS SERVICE!  ✅ EXCELLENT FINANCING OPTIONS - APR AS LOW AS LOW AS 2.99%!  💥💥 FAST & EASY FINANCING - COPY-PASTE & APPLY:  https://www.premiertrucks.net/creditapp.aspx  ✅ TRADE-INS WELCOME! TOP DOLLAR FOR YOUR TRADE-IN!  ✅ ALL VEHICLES INSPECTED AND SERVICED PRIOR TO SALE!  ✅ FRIENDLY NO PRESSURE STAFF - FAST AND EASY PURCHASE!  🦊  📃 CARFAX Available on this: 2016 *RAM* *2500* Laramie Crew Cab LWB 4WD  ⚡️⚡️📲 ☎️️ CALL OR TEXT (336) 203-9408 📲 ☎️️ ⚡️⚡️        More vehicle details: premier-trucks-and-imports.hammerwebsites.net/v/rX09QREU     Address: 731 E King St King, NC 27021   Phone: (336) 203-9408     Website: www.premiertrucks.net/      📲 ☎️ Call or text (336) 203-9408 for quick answers to your questions about this RAM 2500 Your message will always be answered by a real human — never an automated system.     Disclaimer: Diesels, Lifted 4x4s, Crewcabs - Call or Text (336) 203-9408 will never sell, share, or spam your mobile number. Standard text messaging rates may apply. *Front Wheel Drive* *All Wheel Drive* *4 Doors* *Doors* silverado *chevy *4wd *4 wheel drive, black on black, red, white, leather, navigation, lifted, lift, low miles, ltz, lt, z71, truck, sierra, gmc, 2007, 2008, 2009, 2010, 2011, 2012, 2013, 2014, 2015, 2016, 2017, chevrolet, cheyenne, laramie ,st, slt ,xlt, lariat, king ranch, 1500, 2500, 3500, dually, drw, srw, 4x4 ,fx4, 2wd ,4wd, 4 door, four door, crew cab, supercab, extended cab, 1 ton, 1/2 ton, 3/4 ton, heavy duty, hd, superduty, powerstroke, diesel, duramax ,cummins, 5.3l ,6.0l ,5.9L, 6.7L, 6.6L, 6.0L, 6.4L, 7.3L, 6.7l, 5.9, 6.7, 6.0, 5.3, 7.3, 6.6, gas, triton, hemi, z71, lifted, lift kit, 18, 20,22,24 ,33, 35, 37, exhaust, chip, programmer, turbo, grill ,guard, tool box ,auxillary, mileage ,bed liner, gooseneck, 5th wheel ,Quad ,megacab , Lift, lifted, Lifted truck, 4x4, diesel, , ram, chevy, gmc, chevrolet, ford, flat bed, flatbed, western hauler, 1 ton, 1/2 ton, 3/4 ton, heavy duty, hd, superduty, powerstroke, power stroke, diesel, turbo diesel, duramax, cummins, bumper, power stroke, ranch hand, Harley, Harley Davidson, oil field, ranch, welding, welder, transportation, carfax, car fax, financing, warranty, Jeep Wrangler X, Jeep Wrangler Sport, Manual Transmission, SUV, Sport Utility Vehicle, Denali, King Ranch, Lariet, Off Road *suv* *truck* *car* *sedan* *wagon.* used cars for sale* choice preowned auto* *all prices* *all makes* *all models* *all years*, Toyota, tacoma, Tundra, Financing, Credit Union, 6 speed, *Clean* *Cheap* *Used* *Certified* *pre-owned* *Preowned* *Pre owned* *Like New* *fair* *good* *great* fuel, hostile, 20x12, 20x10, 20x9, 22x12, 22x10, 22x14, CREDIT, INSTANT, INSTANT APPROVAL, NO MONEY, NO MONEY DOWN, 0, ${'$'}0 MONEY, *${'$'}1*,0 DOWN, ${'$'}0 DOWN, LOW PAYMENTS, FLEXIBLE PAYMENTS, Premier Trucks, premier, Premier Trucks and Imports, CREDIT UNION, RUST FREE, NO HAGGLE, NO HASSLE, KING, WINSTON SALEM, GREENSBORO, KERNERSVILLE, NORTH CAROLINA, CHARLOTTE, BURLINGTON, ROANOKE, MT. AIRY, Off Road *suv* *truck* *car* *sedan* *wagon.* used cars for sale* choice preowned auto* *all prices* *all makes* *all models* *all years*, Financing, Credit Union, 6 speed, *Clean* *Cheap* *Used* *Certified* *pre-owned* *Preowned* *Pre owned* *Like New* *fair* *good*
 
       2016 RAM 2500 Laramie Crew Cab LWB 4WD   56435bda4ec4411cbc4fa7b69190335f",,nc,36.2768,-80.3395"""
        val parsed = FastCsvLineParser.parseLines(csvRecord)
        assertEquals(1, parsed.size)
        assertEquals(csvRecord, parsed[0].toCsv())
    }
}