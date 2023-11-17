package com.intellisoft.chanjoke.shared

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.intellisoft.chanjoke.R
import com.intellisoft.chanjoke.databinding.FragmentLandingPageBinding

class LandingPage : Fragment() {

    private lateinit var viewModel: LandingPageViewModel
    private lateinit var _binding:FragmentLandingPageBinding
    private val binding get() = _binding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentLandingPageBinding.inflate(inflater, container, false)

        return _binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardViewSearchClient.setOnClickListener {
            findNavController().navigate(R.id.patient_list)
        }



    }


}