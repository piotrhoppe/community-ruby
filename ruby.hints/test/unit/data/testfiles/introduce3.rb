def test
  alreadyexists1 = 40
  if true
     alreadyexists2 = 50
     alreadyexists3 = 50
  end
  notusedinexpression = 60
  [1,2,3].each{|methodcall| puts "not a method call just confusing the below name check" }
  # Start of expression
  newvar = 50
  puts alreadyexists1
  puts alreadyexists2
  [1,2,3].each {|alreadyexists3| puts alreadyexists3 }
  puts methodcall
  alreadyexists2 = alreadyexists1 + 10
  alreadyexists3 = alreadyexists1 + 10
  puts alreadyexists3
  [1,2,3].each {|methodcall2| puts "not a method call just confusing the below name check" }
  notusedoutsideblock = 50
  puts notusedoutsideblock
  notusedanywhere = 30
  usedlater = 30
  # end of refactored expression
  if (true)
    puts usedlater
    puts alreadyexists2
    puts methodcall2
  end
end

